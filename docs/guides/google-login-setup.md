# Google login setup ("Sign in with Google")

What has to exist in Google Cloud so Amazfy can offer "Sign in with Google", and how to give the backend its credentials. About 10 minutes, free.

## How it works (short)

"Sign in with Google" uses **OpenID Connect (OIDC)**, a standard built on OAuth 2.0:

1. The user clicks *Sign in with Google*. The browser opens `GET /api/v1/auth/google` on our backend.
2. The backend redirects to Google's sign-in page, naming our app by its **client ID**.
3. The user picks a Google account and agrees to share their basic profile (name, email).
4. Google redirects the browser to our **redirect URI** (`/api/v1/auth/google/callback`) with a one-time **code**.
5. The backend exchanges that code with Google (using the **client secret**) for an **ID token**: a signed statement "this is Google user 1234, email john@gmail.com, verified".
6. The backend checks the signature, finds or creates the Amazfy user, and logs them in with our own tokens.

This is **login only**. Access to the seller's Google Ads data is a separate consent ("Connect Google Ads", milestone M4), even when it's the same Google account.

## 1. Create a Google Cloud project

1. Open https://console.cloud.google.com and sign in with the Google account that will own Amazfy's setup (ideally a company account, not a personal one).
2. Project picker (top bar) → **New project** → name `Amazfy` → **Create**, and select it.

## 2. Configure the consent screen

In the menu: **APIs & Services → OAuth consent screen** (Google also calls this area **Google Auth Platform**). Click **Get started** if asked.

| Setting | Value |
| --- | --- |
| App name | `Amazfy` (shown to users on Google's page) |
| User support email | your email |
| Audience | **External** (sellers use their own Google accounts) |
| Contact email | your email |

Then:

- **Data access** (or *Scopes*): add `openid`, `.../auth/userinfo.email` and `.../auth/userinfo.profile`. These are "non-sensitive" scopes and need no review by Google.
- **Audience → Test users**: add your own Gmail address and those of anyone else who will try Google sign-in (up to 100).

**Why test users?** A new app starts in **Testing** status: Google lets only the listed test users sign in. Anyone else gets Google's "Access blocked" page. With an empty list, nobody can use Google sign-in, not even you. This affects only Google sign-in; email + password sign-up works for everyone.

**Later, for real sellers:** Audience → **Publish app** switches the status to **In production**, and any Google account can sign in. Because Amazfy asks only for basic profile scopes (name, email), Google's full app review isn't needed, but Google may ask for brand verification (home page, privacy policy, a domain we own) before it shows the app name and logo. That is a pre-beta task (see requirements, *External setup checklist*), not needed for development.

## 3. Create the OAuth client

**Clients** (or *Credentials → Create credentials → OAuth client ID*):

| Setting | Value |
| --- | --- |
| Application type | **Web application** |
| Name | `Amazfy local` |
| Authorized redirect URIs | `http://localhost:8080/api/v1/auth/google/callback` |

**Create**, then copy the **Client ID** (ends with `.apps.googleusercontent.com`) and the **Client secret**. Google shows the secret in full only now; download the JSON or copy it somewhere safe.

The redirect URI must match exactly, character for character: no trailing slash, `http` not `https`, port `8080`. A mismatch shows Google's error page *redirect_uri_mismatch*. Production will get its own client (or an extra redirect URI) with the real `https://` address.

## 4. Give the backend the credentials

Create the file `backend/secrets-local.properties` (Git ignores it, so it is never committed):

```properties
GOOGLE_CLIENT_ID=1234567890-abc123.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-your-secret
```

The local profile loads it automatically (`spring.config.import` in `application-local.properties`). Restart `bootRun`.

Check: open http://localhost:8080/api/v1/auth/google in the browser. You should land on Google's account picker showing *Amazfy*. Without the file, the endpoint answers 501 "Google login is not available yet."

In production, set `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` and `GOOGLE_REDIRECT_URI` as environment variables from the hosting platform's secrets.

## Never

- Commit the client secret or paste it into a chat, ticket or screenshot. If it leaks: Google Cloud → the client → **Add secret**, switch to the new one, then delete the old.
- Add Google Ads scopes to this login client's sign-in request. Ads access gets its own consent later.
