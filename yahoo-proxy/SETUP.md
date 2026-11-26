# Yahoo proxy one-time setup

One-time configuration of the Google Cloud project for the Yahoo proxy. Build and deployment of a new version are
described in `README.md`.

The proxy shares the project, the Artifact Registry repository and the build service account with the backend. The
steps are run once per GCP project, in the order below. All commands use the `gcloud` CLI.

## 1. Prerequisites

- The backend setup in `backend/SETUP.md` is done, at least these sections:
  - 3 (enabled APIs: Cloud Run, Artifact Registry, Cloud Build, IAM)
  - 4 (Artifact Registry repository)
  - 8 (backend runtime service account)
  - 9 (build service account)
- You have the `Owner` role on the project and `gcloud` is logged in:

  ```bash
  gcloud auth login
  ```

The proxy needs no API key, secret, bucket or database.

## 2. Variables

Set these variables in your shell before running the commands. Adjust values as needed; `REGION`, `AR_REPOSITORY`,
`BUILD_SA` and `BACKEND_SA` must match the backend's values.

```bash
export PROJECT_ID="your-project-id"
export REGION="europe-west4"
export SERVICE_NAME="sixteen-screener-yahoo"
export AR_REPOSITORY="sixteen-screener"
export RUNTIME_SA="sixteen-screener-yahoo"
export BUILD_SA="sixteen-screener-build"
export BACKEND_SA="sixteen-screener-backend"
export DEVELOPER_EMAIL="you@example.com"   # your personal Google account
```

```bash
gcloud config set project "$PROJECT_ID"
gcloud config set run/region "$REGION"
```

## 3. Runtime service account

The service runs as its own service account. It calls only Yahoo, so the account gets no roles at all; this keeps it
from using the default Compute Engine service account and its broad project roles.

```bash
gcloud iam service-accounts create "$RUNTIME_SA" \
  --display-name="Sixteen Screener Yahoo proxy runtime"
```

## 4. First deployment

Build and deploy the service once as described in `README.md` ("Build with Cloud Build" and "Deploy to Cloud Run").
The build service account already has write access to the Artifact Registry repository from the backend setup. The
Cloud Run service must exist before invoker permissions can be granted in the next step.

## 5. Access of the backend

The service does not allow unauthenticated calls. Only the backend's runtime service account may call it:

```bash
gcloud run services add-iam-policy-binding "$SERVICE_NAME" \
  --region="$REGION" \
  --member="serviceAccount:${BACKEND_SA}@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role="roles/run.invoker"
```

The backend authenticates with an ID token for the proxy URL, which it gets from the Cloud Run metadata server, so no
key or extra role is needed. The frontend app's service account gets no access.

Check the bindings; `roles/run.invoker` must list the backend's service account only:

```bash
gcloud run services get-iam-policy "$SERVICE_NAME" --region="$REGION"
```

## 6. Temporary access for manual checks

To call the service yourself (see "Call the deployed service" in `README.md`), grant your account access, and remove
it when you are done:

```bash
gcloud run services add-iam-policy-binding "$SERVICE_NAME" \
  --region="$REGION" \
  --member="user:${DEVELOPER_EMAIL}" \
  --role="roles/run.invoker"
```

```bash
gcloud run services remove-iam-policy-binding "$SERVICE_NAME" \
  --region="$REGION" \
  --member="user:${DEVELOPER_EMAIL}" \
  --role="roles/run.invoker"
```

## 7. Verification

With the temporary access from section 6:

```bash
SERVICE_URL="$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)')"
TOKEN="$(gcloud auth print-identity-token)"

curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/health"
curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/symbols/SAP.DE/quote"
```

The first answers `{"status":"ok"}`; the second returns a quote, or `429` if Yahoo blocks requests from the Cloud Run
egress addresses at the moment. A call without a token answers `403`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' "${SERVICE_URL}/health"
```

Remove your access afterwards (section 6). The backend is connected to the proxy by setting `YAHOO_PROXY_URL` to the
service URL in the backend's deployment.
