# Testing the API by hand (Postman)

How to try the backend's endpoints yourself before the frontend pages exist. Automated tests (`./gradlew build`) already check every endpoint; this guide is for seeing it work and exploring.

## 1. Start everything

In the repository root, then in `backend/`:

```bash
docker compose up -d                        # PostgreSQL + Mailpit
cd backend && ./gradlew bootRun             # Windows PowerShell: .\gradlew.bat bootRun
```

Wait for `Started AmazfyBackendApplication`. Check: http://localhost:8080/actuator/health shows `{"status":"UP"}`.

- **Restart `bootRun` after pulling new code or running `./gradlew build`.** A backend left running from before keeps serving old code and gives confusing answers (501 "not available yet", or 500).
- Emails the backend sends appear in **Mailpit**: http://localhost:8025.

## 2. Set up Postman

### Import the API

1. Postman → **Import** → choose `api/openapi.yaml` from the repository.
2. Pick **Postman Collection**. Postman creates a collection *Amazfy API* with a folder per tag (Auth, Me, Accounts, System) and one request per endpoint, with example bodies.
3. Re-import after the API changes (the file is the contract; Postman's copy doesn't update itself).

### Create an environment

**Environments → +** → name it `Amazfy local`, add these variables, save, and select it (top right):

| Variable | Value |
| --- | --- |
| `baseUrl` | `http://localhost:8080` |
| `accessToken` | (empty; filled by a script) |
| `refreshToken` | (empty; filled by a script) |
| `mailToken` | (empty; filled by a script) |

If the imported requests use `{{baseUrl}}` with a different value in the collection's *Variables* tab, set it to `http://localhost:8080` there too.

### Send the access token automatically

Open the collection → **Authorization** tab → Type **Bearer Token** → Token `{{accessToken}}`. Every request inherits it. Public endpoints (register, login, ...) simply ignore it.

### Save tokens after register, login and refresh

Open **Register**, then its **Scripts** tab → **Post-response**, and paste:

```javascript
// Keep the tokens from the response for the next requests
if (pm.response.code === 200 || pm.response.code === 201) {
    pm.environment.set("accessToken", pm.response.json().accessToken);
    const cookie = pm.response.headers.get("Set-Cookie") || "";
    const match = /amazfy_refresh=([^;]*)/.exec(cookie);
    if (match) pm.environment.set("refreshToken", match[1]);
}
```

Paste the same script into **Login** and **Refresh tokens**.

### Send the refresh cookie

The refresh token travels only in the `amazfy_refresh` cookie (the browser keeps it; JavaScript can't read it). In Postman, add it yourself to **Refresh tokens**, **Logout**: **Headers** tab → key `Cookie`, value `amazfy_refresh={{refreshToken}}`.

(Postman's own cookie jar may skip the cookie because it is marked `Secure` and local development uses plain `http`; the explicit header always works.)

### Read the token from the latest email

Emailed links contain a token (`...?token=abc123`). Instead of copying it from Mailpit by hand, add a request to the collection:

- **GET** `http://localhost:8025/api/v1/message/latest`, Authorization: **No Auth**
- **Scripts → Post-response**:

```javascript
// Take the token out of the link in the newest email in Mailpit
const match = /token=([A-Za-z0-9_-]+)/.exec(pm.response.json().Text);
pm.environment.set("mailToken", match ? match[1] : "");
console.log("mailToken:", match ? match[1] : "no token in the latest email");
```

Name it `Mailpit: latest email token`. Then use `{"token": "{{mailToken}}"}` as the body of **Verify email** and `{"token": "{{mailToken}}", "newPassword": "..."}` for **Reset password**.

## 3. Walkthroughs

Each walkthrough lists the requests in order and what you should see. Use a new email each time you start over (e.g. `you+1@example.com`, `you+2@example.com`); sign-up refuses an email that is already registered (409).

### Sign up and look around

1. **Register**: `{"email": "john@aqualife.com", "password": "correct horse battery", "accountName": "AquaLife US", "region": "NA"}` → **201**, `accessToken` saved.
2. **Get me** → **200**: your email, `"emailVerified": false`, one account *AquaLife US* with role `OWNER`. Copy the account's `id`.
3. **Get account** with that id → **200**.

### Verify the email

1. Open Mailpit (http://localhost:8025): an email *Confirm your email address for Amazfy*.
2. **Mailpit: latest email token** → `mailToken` saved.
3. **Verify email** with `{"token": "{{mailToken}}"}` → **204**.
4. **Verify email** again → **400** "This link is invalid, has expired or was already used."
5. **Get me** → `"emailVerified": true`.

(**Resend verification** sends a new link and makes the old one stop working; more than 3 per hour → **429**.)

### Log in, refresh, log out

1. **Login** with the email and password → **200**, new tokens saved.
2. **Refresh tokens** (with the `Cookie` header) → **200**, a new access token and a new refresh token. The old refresh token now stops working: sending it again → **401**, and for safety it also ends the newer session (reuse detection).
3. **Logout** (with the cookie) → **204**. Refreshing afterwards → **401**.
4. The access token lives 15 minutes; after that, protected requests return **401** until you refresh or log in again.

### Accounts and access rules

1. **Create account** `{"name": "AquaLife EU"}` → **201**. **Get me** now lists two accounts.
2. **Rename account** (PATCH) one of them → **200** with the new name.
3. Register a **second user** (another email), then call **Get account** / **Rename account** with the **first** user's account id → **403** "You don't have access to this." Users only ever reach accounts they are a member of.

### Forgot and reset password

1. **Forgot password** `{"email": "john@aqualife.com"}` → **202**. Try an email that doesn't exist → also **202** (the API never reveals who has an account).
2. **Mailpit: latest email token**.
3. **Reset password** `{"token": "{{mailToken}}", "newPassword": "a brand new passphrase"}` → **204**.
4. **Login** with the old password → **401**; with the new one → **200**. Earlier refresh tokens no longer work (reset logs out everywhere).

### Brute-force limits

1. **Login** with a wrong password 5 times → **401** each time.
2. The 6th try, even with the right password → **429** "Too many attempts. Please try again in N minutes", and a `Retry-After` header (seconds).
3. It unlocks by itself 15 minutes after the first failed try. To start fresh sooner, restart with an empty database: `docker compose down -v`, `docker compose up -d`, restart `bootRun`.

### Connect Google Ads

Needs the [Google Ads setup](google-ads-setup.md) and a browser, since Google's consent page and the callback's cookie can't be done from Postman.

1. Log in to Amazfy as an owner and open **Ads**. It says **Not connected**.
2. **Connect Google Ads** → choose the Google login with the test accounts → allow access. Google warns that the app is unverified, which is expected while it's in Testing: **Advanced → Go to Amazfy**. Google words the permission broadly, covering changes as well as reading, because it's the only permission Google offers for Google Ads. For now, Amazfy only reads.
3. You return to **Ads**, which says **Connected**. In pgAdmin, `google_ads_connections` holds an encrypted token starting with `v1.`, never Google's `1//…`.
4. **Connect Google Ads** again and press **Cancel** on Google's page → back on **Ads**, with no message.
5. **Disconnect** → **Not connected**, and the row is gone.

In Postman, **Get Google Ads connection** answers `connected: true` or `false`. As an editor or viewer it still works, while starting and disconnecting answer **403**.

## 4. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| **401** on Get me / accounts | No or expired access token: run **Login** (or **Refresh tokens**) again; check the environment is selected (top right). |
| **401** on Refresh tokens | Missing `Cookie: amazfy_refresh={{refreshToken}}` header, or the token was already used / logged out. Log in again. |
| **501** "not available yet" | The endpoint is built in a later step (e.g. Google login), or an old `bootRun` is still running: restart it. |
| **500** right after pulling code | Old `bootRun` running against new compiled files: stop it (Ctrl+C) and start it again. |
| **429** | A brute-force limit; wait the `Retry-After` seconds or reset the database (see above). |
| **400** with `errors` | Invalid input; each entry names the field (`email`, `password`, ...). |
| No email in Mailpit | `docker compose up -d`; check http://localhost:8025 loads. Emails are sent in the background, so wait a second. |

## 5. In VS Code: `api/amazfy.http`

`api/amazfy.http` holds ready-made requests for every endpoint, in the order of the walkthroughs above. It needs the VS Code extension **REST Client** (`humao.rest-client`); VS Code suggests it when you open the repository.

1. Open `api/amazfy.http`; each request has a **Send Request** link above it. The response opens on the right.
2. Run **Register**, then **Login**: later requests take the access token and refresh cookie from the Login response automatically (`{{login.response.body.$.accessToken}}`).
3. Run **Who am I?** before the account requests: they take the account id from it.
4. Requests that need a token from an email (verify, reset) ask for it in a box at the top of VS Code: run **The newest email in Mailpit** and copy the part after `token=`.
5. To start over, change `@email` at the top of the file (sign-up refuses an existing email).

## 6. Without Postman

The same calls work from a terminal with `curl` (Git Bash), for example:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@aqualife.com","password":"correct horse battery"}'
```

`-i` shows the response headers, including `Set-Cookie` with the refresh token.
