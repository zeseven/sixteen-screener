# Android app one-time setup

One-time configuration of the service account the Android app uses to call the backend. Building and installing a
new app version is described in `README.md`.

The app authenticates with a dedicated service account whose key is bundled into the APK. The account can only invoke
the Cloud Run service (`roles/run.invoker` on the service, no project-level roles). All commands use the `gcloud` CLI.

## 1. Prerequisites

- The backend one-time setup in `backend/SETUP.md` is done up to section 10: the project exists, the IAM API is
  enabled and the Cloud Run service has been deployed once
- `gcloud` CLI logged in with an account that has the `Owner` role on the project
- The organization policy allows service account key creation. Check the effective policy:

  ```bash
  gcloud resource-manager org-policies describe iam.disableServiceAccountKeyCreation \
    --project="$PROJECT_ID" --effective
  ```

  If it shows `enforce: true`, an organization policy administrator has to allow key creation for this project first.

## 2. Variables

Set the variables from `backend/SETUP.md` section 2 (`PROJECT_ID`, `REGION`, `SERVICE_NAME`) and these:

```bash
export APP_SA="sixteen-screener-app"
export APP_SA_EMAIL="${APP_SA}@${PROJECT_ID}.iam.gserviceaccount.com"
export KEY_DIR="$HOME/.sixteen-screener"           # outside the repository
export KEY_FILE="${KEY_DIR}/${APP_SA}-key.json"
```

## 3. Service account

```bash
gcloud iam service-accounts create "$APP_SA" \
  --project="$PROJECT_ID" \
  --display-name="Sixteen Screener Android app"
```

## 4. Access to the Cloud Run service

Grant `roles/run.invoker` on the backend service only:

```bash
gcloud run services add-iam-policy-binding "$SERVICE_NAME" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --member="serviceAccount:${APP_SA_EMAIL}" \
  --role="roles/run.invoker"
```

Check the bindings (the app's account and your personal account from `backend/SETUP.md` section 11):

```bash
gcloud run services get-iam-policy "$SERVICE_NAME" --project="$PROJECT_ID" --region="$REGION"
```

## 5. Key creation

Create the key in a folder outside the repository, readable only by you:

```bash
mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"

gcloud iam service-accounts keys create "$KEY_FILE" \
  --project="$PROJECT_ID" \
  --iam-account="$APP_SA_EMAIL"

chmod 600 "$KEY_FILE"
```

Never copy the key into the repository. `.gitignore` contains patterns for key files as a second line of defence.

## 6. Key location

Point the build to the key in the user-level Gradle properties (once per machine):

```bash
mkdir -p "$HOME/.gradle"
echo "sixteen.serviceAccountKey=${KEY_FILE}" >> "$HOME/.gradle/gradle.properties"
```

Alternatively export `SIXTEEN_SERVICE_ACCOUNT_KEY="$KEY_FILE"` in the shell before building (see `README.md`).

## 7. Key rotation

Rotate the key when it may have leaked (for example an APK was shared) or periodically.

List the existing keys and note the ID of the current one:

```bash
gcloud iam service-accounts keys list \
  --project="$PROJECT_ID" \
  --iam-account="$APP_SA_EMAIL" \
  --managed-by=user
```

Create a new key, replacing the file the build points to:

```bash
mv "$KEY_FILE" "${KEY_FILE}.old"

gcloud iam service-accounts keys create "$KEY_FILE" \
  --project="$PROJECT_ID" \
  --iam-account="$APP_SA_EMAIL"

chmod 600 "$KEY_FILE"
```

Rebuild the APK and install it on every device as described in `README.md` (`./gradlew assembleDebug`,
`adb install -r`). Then delete the old key and its local copy:

```bash
OLD_KEY_ID="id-of-the-old-key"   # from the keys list above

gcloud iam service-accounts keys delete "$OLD_KEY_ID" \
  --project="$PROJECT_ID" \
  --iam-account="$APP_SA_EMAIL"

rm "${KEY_FILE}.old"
```

Devices still running an APK with the deleted key can no longer call the backend until the new APK is installed.
