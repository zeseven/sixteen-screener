# Switch the backend to the named Firestore database

One-time steps to move an existing deployment from the `(default)` Firestore database to the named database
`sixteen-screener` (ADR-067). New projects follow `backend/SETUP.md` instead. Use the variables from
`backend/SETUP.md` section 2.

The existing data is not migrated. Without existing profiles, `(default)` only holds cached source data, which the
backend downloads again.

## 1. Create the named database

```bash
export FIRESTORE_DATABASE="sixteen-screener"

gcloud firestore databases create \
  --database="$FIRESTORE_DATABASE" \
  --location="$FIRESTORE_LOCATION" \
  --type=firestore-native \
  --delete-protection
```

## 2. Enable TTL policies

```bash
for COLLECTION in sourceData quoteHistory requestCounters logoMisses secCik; do
  gcloud firestore fields ttls update expireAt \
    --database="$FIRESTORE_DATABASE" \
    --collection-group="$COLLECTION" \
    --enable-ttl \
    --async
done

gcloud firestore fields ttls list --database="$FIRESTORE_DATABASE"
```

## 3. Grant the runtime service account access to the named database

The unconditional binding stays until the new revision runs, so the current revision keeps working.

```bash
RUNTIME_SA_EMAIL="${RUNTIME_SA}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA_EMAIL}" \
  --role="roles/datastore.user" \
  --condition="expression=resource.name==\"projects/${PROJECT_ID}/databases/${FIRESTORE_DATABASE}\",title=sixteen-screener-database-only"
```

## 4. Build and deploy the new image

The image must contain the backend version that supports `FIRESTORE_DATABASE`. Build and deploy it as described in
`backend/README.md` (sections "Build with Cloud Build" and "Deploy to Cloud Run"). The backend uses
`sixteen-screener` by default, so the deploy command needs no new parameters.

Check that the new revision answers, for example with `GET /profiles/does-not-exist` (expected `404` from the app).

## 5. Remove the unconditional access

```bash
gcloud projects remove-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA_EMAIL}" \
  --role="roles/datastore.user" \
  --condition=None
```

## 6. Optional: delete the `(default)` database

Only if no other service in the project uses it. This can't be undone.

```bash
gcloud firestore databases describe --database="(default)"
gcloud firestore databases delete --database="(default)"
```
