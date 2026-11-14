# Backend one-time setup

One-time configuration of the Google Cloud project for the backend. Build and deployment of a new
version are described in `README.md`.

The steps are run once per GCP project, in the order below. All commands use the `gcloud` CLI.

## 1. Prerequisites

- Google Cloud project with billing enabled; you have the `Owner` role on it
- `gcloud` CLI installed and logged in:

  ```bash
  gcloud auth login
  ```

- A Finnhub account (free plan)
- A contact email for the SEC EDGAR `User-Agent` header (required by the SEC fair access policy)

## 2. Variables

Set these variables in your shell before running the commands. Adjust values as needed.

```bash
export PROJECT_ID="your-project-id"
export REGION="europe-west4"               # Cloud Run, Artifact Registry, GCS
export FIRESTORE_LOCATION="eur3"           # Firestore multi-region (or a region, e.g. europe-west4)
export SERVICE_NAME="sixteen-screener-backend"
export AR_REPOSITORY="sixteen-screener"
export LOGO_BUCKET="${PROJECT_ID}-sixteen-screener-logos"
export RUNTIME_SA="sixteen-screener-backend"
export BUILD_SA="sixteen-screener-build"
export DEVELOPER_EMAIL="you@example.com"   # your personal Google account
```

```bash
gcloud config set project "$PROJECT_ID"
gcloud config set run/region "$REGION"
```

## 3. Enable APIs

```bash
gcloud services enable \
  run.googleapis.com \
  firestore.googleapis.com \
  storage.googleapis.com \
  secretmanager.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  iam.googleapis.com
```

## 4. Artifact Registry

Docker repository for backend images:

```bash
gcloud artifacts repositories create "$AR_REPOSITORY" \
  --repository-format=docker \
  --location="$REGION" \
  --description="Sixteen Screener images"
```

## 5. Firestore

Create the default database in Native mode. The location can't be changed later.

```bash
gcloud firestore databases create \
  --location="$FIRESTORE_LOCATION" \
  --type=firestore-native
```

Enable TTL policies on the `expireAt` field of all cache collections:

```bash
for COLLECTION in sourceData quoteHistory requestCounters logoMisses secCik; do
  gcloud firestore fields ttls update expireAt \
    --collection-group="$COLLECTION" \
    --enable-ttl \
    --async
done
```

Check the TTL policies (state becomes `ACTIVE` after a few minutes):

```bash
gcloud firestore fields ttls list
```

The other collections (`profiles`, `profileNames`, `sourceCalls`) have no TTL. No composite indexes are needed.

## 6. Cloud Storage bucket for logos and exchange directories

The bucket is private; it stores cached SVG logos (`logos/{symbol}.svg`) and daily exchange directory
snapshots (`directory/{source}/{exchangeCode}.json`, ADR-034). All files are served or managed by the backend.

```bash
gcloud storage buckets create "gs://${LOGO_BUCKET}" \
  --location="$REGION" \
  --uniform-bucket-level-access \
  --public-access-prevention
```

## 7. Secrets

### Finnhub API key

Get the key manually:
1. Sign up or log in at https://finnhub.io
2. Open the Dashboard
3. Copy the API key shown on the dashboard page

Store it in Secret Manager (the key is read from the terminal and not saved in the shell history):

```bash
read -rs FINNHUB_API_KEY
printf '%s' "$FINNHUB_API_KEY" | gcloud secrets create finnhub-api-key \
  --replication-policy=automatic \
  --data-file=-
unset FINNHUB_API_KEY
```

### SEC EDGAR User-Agent

The SEC requires a `User-Agent` header with an application name and a contact email.
It is kept in Secret Manager so the email does not end up in the repository.

```bash
printf '%s' "SixteenScreener ${DEVELOPER_EMAIL}" | gcloud secrets create sec-edgar-user-agent \
  --replication-policy=automatic \
  --data-file=-
```

### Rotating a secret

Add a new version; the next deployment (or a new Cloud Run revision) picks up `latest`:

```bash
read -rs FINNHUB_API_KEY
printf '%s' "$FINNHUB_API_KEY" | gcloud secrets versions add finnhub-api-key --data-file=-
unset FINNHUB_API_KEY
```

## 8. Runtime service account

The Cloud Run service runs as a dedicated service account with access to Firestore, the logo bucket
and the two secrets only.

```bash
gcloud iam service-accounts create "$RUNTIME_SA" \
  --display-name="Sixteen Screener backend runtime"

RUNTIME_SA_EMAIL="${RUNTIME_SA}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA_EMAIL}" \
  --role="roles/datastore.user" \
  --condition=None

gcloud storage buckets add-iam-policy-binding "gs://${LOGO_BUCKET}" \
  --member="serviceAccount:${RUNTIME_SA_EMAIL}" \
  --role="roles/storage.objectAdmin"

for SECRET in finnhub-api-key sec-edgar-user-agent; do
  gcloud secrets add-iam-policy-binding "$SECRET" \
    --member="serviceAccount:${RUNTIME_SA_EMAIL}" \
    --role="roles/secretmanager.secretAccessor"
done
```

## 9. Build service account

Cloud Run needs a `linux/amd64` image, so the native image is built with Cloud Build (building it locally
on an Apple Silicon Mac requires `amd64` emulation and is very slow). Cloud Build runs as a dedicated service account that can push images
and write build logs.

```bash
gcloud iam service-accounts create "$BUILD_SA" \
  --display-name="Sixteen Screener backend build"

BUILD_SA_EMAIL="${BUILD_SA}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud artifacts repositories add-iam-policy-binding "$AR_REPOSITORY" \
  --location="$REGION" \
  --member="serviceAccount:${BUILD_SA_EMAIL}" \
  --role="roles/artifactregistry.writer"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${BUILD_SA_EMAIL}" \
  --role="roles/logging.logWriter" \
  --condition=None

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${BUILD_SA_EMAIL}" \
  --role="roles/storage.objectViewer" \
  --condition=None

PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"

gcloud iam service-accounts add-iam-policy-binding "$BUILD_SA_EMAIL" \
  --member="serviceAccount:service-${PROJECT_NUMBER}@gcp-sa-cloudbuild.iam.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"
```

The `storage.objectViewer` role lets the build read the uploaded sources from the
`gs://${PROJECT_ID}_cloudbuild` staging bucket, which `gcloud builds submit` creates on first use.
Granting `roles/iam.serviceAccountUser` allows the Cloud Build service agent to execute builds as this service account.
`cloudbuild.yaml` configures `options: { logging: CLOUD_LOGGING_ONLY }`, so build logs are written directly to Cloud Logging via `roles/logging.logWriter`.

## 10. First deployment

Build and deploy the service once as described in `README.md`. The Cloud Run service must exist before
invoker permissions can be granted in the next step.

## 11. Access to the service

The service does not allow unauthenticated calls. Grant `roles/run.invoker` to your personal account:

```bash
gcloud run services add-iam-policy-binding "$SERVICE_NAME" \
  --region="$REGION" \
  --member="user:${DEVELOPER_EMAIL}" \
  --role="roles/run.invoker"
```

The frontend app's service account is created and granted `roles/run.invoker` as described in
`frontend/SETUP.md`.

Check the bindings:

```bash
gcloud run services get-iam-policy "$SERVICE_NAME" --region="$REGION"
```

## 12. Verification

Call the service with your identity token. Create or retrieve a test profile first, then use its returned UUID to query its sources:

```bash
SERVICE_URL="$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)')"

# 1. Get or create a profile by name (verifies Cloud Run IAM and Firestore read/write)
PROFILE_RESPONSE="$(curl -s -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
  -H "Content-Type: application/json" \
  -d '{"name": "test"}' \
  "${SERVICE_URL}/profiles")"

echo "$PROFILE_RESPONSE"

PROFILE_ID="$(printf '%s' "$PROFILE_RESPONSE" | sed -E 's/.*"id":"([^"]+)".*/\1/')"

# 2. Query sources using the profile UUID
curl -s -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
  "${SERVICE_URL}/profiles/${PROFILE_ID}/sources"
```

A call without a token must be rejected with `403`:

```bash
curl -s -o /dev/null -w '%{http_code}\n' "${SERVICE_URL}/profiles"
```
