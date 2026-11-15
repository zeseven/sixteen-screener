# Sixteen Screener backend

Kotlin service built with Ktor. It collects stock data from external sources (Finnhub, SEC EDGAR, logoapi.sh),
caches it in Firestore and Cloud Storage, and serves the REST API described in `docs/openapi.yaml`.
The service is compiled into a GraalVM native image and runs on Cloud Run.

One-time configuration of the GCP project (Firestore, bucket, secrets, service accounts, IAM) is described in
`SETUP.md`. All commands below are run from the `backend` folder unless stated otherwise.

## Prerequisites

- JDK 21 (the Gradle wrapper downloads Gradle itself)
- Docker (for the Firestore emulator tests and local native image builds)
- `gcloud` CLI (for running against a real project, building and deploying)

## Configuration

Static configuration is in `src/main/resources/application.conf`: the list of sources with their base URLs,
capabilities, request limits and timeouts. Environment variables override or complete it:

| Variable                  | Required           | Description                                                                 |
|---------------------------|--------------------|-----------------------------------------------------------------------------|
| `GCP_PROJECT_ID`          | yes                | GCP project with Firestore and the bucket                                   |
| `FIRESTORE_DATABASE`      | no                 | Named Firestore database (default `sixteen-screener`)                       |
| `LOGO_BUCKET`             | yes                | Bucket for cached logos and exchange directories                            |
| `DEFAULT_SOURCE`          | no                 | Default source ID (default `finnhub`)                                       |
| `FINNHUB_API_KEY`         | if Finnhub enabled | Finnhub API key (Secret Manager on Cloud Run)                               |
| `SEC_EDGAR_USER_AGENT`    | if SEC enabled     | `User-Agent` for SEC EDGAR: app name and contact email (Secret Manager)     |
| `PORT`                    | no                 | HTTP port (default `8080`; set by Cloud Run)                                |
| `FIRESTORE_EMULATOR_HOST` | no                 | Use the Firestore emulator, e.g. `localhost:8085` (local runs and tests)    |
| `STORAGE_EMULATOR_HOST`   | no                 | Use a Cloud Storage emulator without credentials, e.g. `http://localhost:4443` |

The service validates the configuration on start and exits with a list of all problems found.
Logs are written to stdout as JSON in the Cloud Logging format.

## Tests

```bash
./gradlew test
```

Unit and HTTP route tests are hermetic: external sources are replaced with recorded responses and storage with
in-memory implementations.

### Firestore emulator tests

The Firestore adapters are tested against the Firestore emulator in a separate task. Start the emulator in Docker:

```bash
docker run -d --name firestore-emulator -p 8085:8080 \
  --entrypoint java gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators \
  -XX:UseSVE=0 \
  -cp /google-cloud-sdk/platform/cloud-firestore-emulator/cloud-firestore-emulator.jar \
  com.google.cloud.datastore.emulator.firestore.CloudFirestore start --host=0.0.0.0 --port=8080
```

The emulator is started directly with `java` because `gcloud emulators firestore start` crashes on Apple Silicon;
`-XX:UseSVE=0` is needed there as well and can be dropped on `amd64` machines.

Run the tests and remove the container:

```bash
FIRESTORE_EMULATOR_HOST=localhost:8085 ./gradlew emulatorTest
docker rm -f firestore-emulator
```

## Run locally on the JVM

Against emulators: start the Firestore emulator as above and a Cloud Storage emulator with a bucket:

```bash
docker run -d --name gcs-emulator -p 4443:4443 fsouza/fake-gcs-server \
  -scheme http -port 4443 -external-url http://localhost:4443
curl -s -X POST http://localhost:4443/storage/v1/b \
  -H 'Content-Type: application/json' -d '{"name":"local-logos"}'
```

```bash
GCP_PROJECT_ID=local \
LOGO_BUCKET=local-logos \
FIRESTORE_EMULATOR_HOST=localhost:8085 \
STORAGE_EMULATOR_HOST=http://localhost:4443 \
FINNHUB_API_KEY=your-finnhub-key \
SEC_EDGAR_USER_AGENT="SixteenScreener you@example.com" \
./gradlew run
```

Against a real project: log in with Application Default Credentials and drop the emulator variables:

```bash
gcloud auth application-default login
GCP_PROJECT_ID="$PROJECT_ID" LOGO_BUCKET="$LOGO_BUCKET" \
FINNHUB_API_KEY=your-finnhub-key SEC_EDGAR_USER_AGENT="SixteenScreener you@example.com" \
./gradlew run
```

The API is available at `http://localhost:8080`, for example:

```bash
curl -s -H 'Content-Type: application/json' -d '{"name":"test"}' http://localhost:8080/profiles
```

## Build a native image locally

```bash
docker build -t sixteen-screener-backend .
```

The image is built for the host architecture. The native image build needs more than 6 GB of heap, so give the
Docker VM at least 12 GB of memory; with less the builder's watchdog aborts the build in `[6/8] Compiling methods`. Cloud Run needs a `linux/amd64` image; on Apple Silicon build it with Cloud Build
(below) instead of emulating `amd64` locally.

Run the image against the emulators started above:

```bash
docker run --rm -p 8080:8080 \
  -e GCP_PROJECT_ID=local -e LOGO_BUCKET=local-logos \
  -e FIRESTORE_EMULATOR_HOST=host.docker.internal:8085 \
  -e STORAGE_EMULATOR_HOST=http://host.docker.internal:4443 \
  -e FINNHUB_API_KEY=your-finnhub-key \
  -e SEC_EDGAR_USER_AGENT="SixteenScreener you@example.com" \
  sixteen-screener-backend
```

## Build with Cloud Build

Set the variables from `SETUP.md` section 2, then run from the repository root:

```bash
TAG="$(git rev-parse --short HEAD)"

gcloud builds submit backend \
  --config=backend/cloudbuild.yaml \
  --region="$REGION" \
  --service-account="projects/${PROJECT_ID}/serviceAccounts/${BUILD_SA}@${PROJECT_ID}.iam.gserviceaccount.com" \
  --substitutions=_REGION="$REGION",_AR_REPOSITORY="$AR_REPOSITORY",_SERVICE_NAME="$SERVICE_NAME",_TAG="$TAG"
```

The build runs the `Dockerfile` on a 32-CPU, 32 GB machine (`E2_HIGHCPU_32`) and pushes the image to Artifact Registry. Files listed in
`.gcloudignore` (tests, build outputs) are not uploaded.

## Deploy to Cloud Run

```bash
gcloud run deploy "$SERVICE_NAME" \
  --region="$REGION" \
  --image="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPOSITORY}/${SERVICE_NAME}:${TAG}" \
  --service-account="${RUNTIME_SA}@${PROJECT_ID}.iam.gserviceaccount.com" \
  --no-allow-unauthenticated \
  --min-instances=0 \
  --max-instances=2 \
  --cpu-boost \
  --cpu=1 \
  --memory=512Mi \
  --set-env-vars=GCP_PROJECT_ID="$PROJECT_ID",LOGO_BUCKET="$LOGO_BUCKET",DEFAULT_SOURCE=finnhub \
  --set-secrets=FINNHUB_API_KEY=finnhub-api-key:latest,SEC_EDGAR_USER_AGENT=sec-edgar-user-agent:latest
```

- `--no-allow-unauthenticated`: only principals with `roles/run.invoker` can call the service (`SETUP.md` section 11)
- `--min-instances=0`: the service scales to zero when idle; the native image keeps the cold start short
- `--max-instances=2`: caps the number of instances and therefore the cost
- `--cpu-boost`: extra CPU during startup
- `--service-account`: runtime service account with access to Firestore, the bucket and the secrets only
- `--set-secrets`: source credentials are read from Secret Manager into environment variables; the `latest`
  version is resolved when a new revision starts

## Call the API

```bash
SERVICE_URL="$(gcloud run services describe "$SERVICE_NAME" --region="$REGION" --format='value(status.url)')"
TOKEN="$(gcloud auth print-identity-token)"

curl -s -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d '{"name":"test"}' "${SERVICE_URL}/profiles"

curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/profiles/${PROFILE_ID}/search?query=apple"
curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/profiles/${PROFILE_ID}/companies/AAPL/price-ratios"
curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/profiles/${PROFILE_ID}/sources"
curl -s -H "Authorization: Bearer ${TOKEN}" "${SERVICE_URL}/logos/AAPL"
```

`PROFILE_ID` is the `id` returned by `POST /profiles`. Identity tokens expire after one hour.

## Native image notes

- The build uses Liberica Native Image Kit (GraalVM CE for JDK 21). Reachability metadata for the application is in
  `src/main/resources/META-INF/native-image/`; metadata for libraries comes from the GraalVM reachability metadata
  repository. `-march=compatibility` keeps the binary runnable on any `x86-64` CPU Cloud Run may use.
- `src/main/native/scoped_memory_access_stub.c` provides `ScopedMemoryAccess.closeScope0`. Netty 4.2 inside
  `grpc-netty-shaded` (used by the Firestore client) references the Java foreign memory API, whose native method
  GraalVM for JDK 21 cannot link. The method is never called on JDK 21. The stub is compiled by the
  `compileNativeStubs` task and linked into the image. It can be removed after moving to a GraalVM release for a
  newer JDK (22 or later) that links the method itself.
- `main()` sets `io.grpc.netty.shaded.io.netty.allocator.type=pooled` because Netty's default adaptive allocator
  fails to initialise in the native image. It can be removed once a Netty version shipped with `grpc-netty-shaded`
  fixes this; check by building the image without it and calling a Firestore-backed endpoint.
- The runtime image is `distroless/base` with `libz` copied from Debian, which the native image needs.
