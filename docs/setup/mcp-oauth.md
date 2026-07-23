# MCP OAuth Setup

NutRun is an OAuth protected resource. Authorization-code and PKCE are provided by
a managed OAuth authorization server; the Cloud Run service validates its access
tokens and never accepts Firebase mobile tokens on `/mcp`.

Configure the authorization server with:

- Resource/audience: the value of `OAUTH_AUDIENCE` (default `nutrun-mcp`).
- Redirect URIs: each approved MCP client's exact callback URI.
- Authorization-code grant with PKCE `S256`; disable implicit and password grants.
- Access-token claims: `scope` plus `firebase_uid`, where `firebase_uid` is the
  Firebase account linked to the OAuth subject.
- Scopes: `health.read`, `logs.write`, `walks.read`, `location.read`,
  `profile.write`, and `destructive`.

Set these Cloud Run variables:

- `PUBLIC_BASE_URL`
- `OAUTH_ISSUER`
- `OAUTH_AUDIENCE`
- `ALLOWED_MCP_ORIGINS`

Only set `OAUTH_SUBJECT_IS_FIREBASE_UID=true` when the provider's immutable
OAuth `sub` claim is exactly the Firebase UID. Otherwise `firebase_uid` is
required. Account deletion adds the Firebase UID to the server-side revocation
registry, so already-issued MCP access tokens stop working immediately.

The authorization server's discovery document must advertise the authorization
and token endpoints, `authorization_code`, and `S256`. NutRun publishes its
protected-resource metadata at `/.well-known/oauth-protected-resource`.
