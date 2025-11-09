## Multi-arch Docker image build procedure for EDC-CE

This document describes the exact steps I followed to build and verify multi-architecture Docker images for the sovity EDC-CE components.

### Components built
- `ghcr.io/takuma-8trees/edc-ce-ui:latest` (frontend)
- `ghcr.io/takuma-8trees/edc-ce:latest` (connector/backend)

---

## Prerequisites

1. Docker with buildx support (Docker 20.10+)
2. QEMU for multi-arch emulation
3. GitHub credentials (PAT with `read:packages` and `write:packages` for GHCR)
4. For frontend: Node.js (project uses yarn 4.1.0 bundled)
5. For backend: JDK 21+ and Gradle
6. GitHub PAT with repository read access (for Gradle dependencies from GitHub Packages)

---

## 1. Setup buildx builder (one-time)

```bash
# Create and use a builder
docker buildx create --name sovity-builder --use

# Register QEMU emulators for cross-building
docker run --rm --privileged tonistiigi/binfmt:latest --install all

# Verify builder
docker buildx inspect --bootstrap
```

---

## 2. Build and push edc-ce-ui (frontend)

### 2.1 Build Next.js artifacts

```bash
cd /home/takuma/sovity/edc-ce/frontend

# Install dependencies using the bundled yarn 4.1.0
node .yarn/releases/yarn-4.1.0.cjs install

# Build Next.js app (generates .next/standalone and .next/static)
node .yarn/releases/yarn-4.1.0.cjs next:build

# Verify build outputs exist
ls -la .next/standalone
ls -la .next/static
```

**Important notes:**
- The project uses yarn v4 (Berry), not yarn v1 (classic)
- System yarn might be v1, so use the bundled `yarn-4.1.0.cjs` explicitly
- The Dockerfile expects `.next/standalone`, `.next/static`, and `public` directories

### 2.2 Build and push multi-arch image

```bash
cd /home/takuma/sovity/edc-ce/frontend

# Login to GHCR (one-time per session)
echo $GHCR_PAT | docker login ghcr.io -u takuma-8trees --password-stdin

# Build and push
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  --build-arg NEXT_PUBLIC_BUILD_VERSION_ARG="$(git -C /home/takuma/sovity/edc-ce rev-parse --short HEAD 2>/dev/null || echo local)" \
  --build-arg NEXT_PUBLIC_BUILD_DATE_ARG="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  -t ghcr.io/takuma-8trees/edc-ce-ui:latest \
  -f Dockerfile \
  .
```

**Build time:** ~2 minutes (after initial layer caching)

### 2.3 Verify frontend image

```bash
# Inspect manifest (should show linux/amd64 and linux/arm64)
docker buildx imagetools inspect ghcr.io/takuma-8trees/edc-ce-ui:latest
```

Expected output:
```
Name:      ghcr.io/takuma-8trees/edc-ce-ui:latest
MediaType: application/vnd.oci.image.index.v1+json
Digest:    sha256:1c8cca02f7734fa8f76ea5cddeaa7f8d0f386cf328206d8ead9df188947c30cd

Manifests: 
  Platform:    linux/amd64
  Platform:    linux/arm64
```

---

## 3. Build and push edc-ce (connector)

### 3.1 Configure GitHub credentials for Gradle

The connector build needs GitHub credentials to access private Maven packages. The build script checks for either `GPR_USER`/`GPR_KEY` or `USERNAME`/`TOKEN` environment variables.

**Option A: Environment variables (recommended)**
```bash
export GPR_USER=your-github-username
export GPR_KEY=your-github-pat-with-repo-read
# Or alternatively:
# export USERNAME=your-github-username
# export TOKEN=your-github-pat-with-repo-read
```

**Option B: Gradle properties** (create `~/.gradle/gradle.properties`)
```properties
gpr.user=your-github-username
gpr.key=your-github-pat-with-repo-read
```

### 3.2 Ensure Java 21 is configured

```bash
# Verify Java version
java -version
# Should show: openjdk version "21.0.x"

# If not Java 21, set JAVA_HOME (example for Ubuntu/Debian)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture)
export PATH=$JAVA_HOME/bin:$PATH
```

### 3.3 Build connector JAR

```bash
cd /home/takuma/sovity/edc-ce/connector

# Build the connector module (skip tests for faster build)
./gradlew :ce:docker-image-ce:build -x test

# Verify the JAR exists
ls -la ce/docker-image-ce/build/libs/app.jar
```

**Build time:** ~5-10 minutes (first build with dependency download)
✅ Verified on 2025-11-09: Build successful with Java 21 and GPR credentials

### 3.4 Build and push multi-arch image

```bash
cd /home/takuma/sovity/edc-ce/connector

# Build and push from ce/docker-image-ce directory (Dockerfile context)
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --push \
  --build-arg SOVITY_BUILD_VERSION_ARG="$(git -C /home/takuma/sovity/edc-ce rev-parse --short HEAD 2>/dev/null || echo local)" \
  --build-arg SOVITY_BUILD_DATE_ARG="$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  -t ghcr.io/takuma-8trees/edc-ce:latest \
  -f ce/docker-image-ce/src/main/docker/Dockerfile \
  ce/docker-image-ce
```

**Build time:** ~2-3 minutes (after JAR is ready)
✅ Verified on 2025-11-09: Successfully pushed to GHCR

**Important notes:**
- The Dockerfile expects `build/libs/app.jar` in the build context
- Build context is `ce/docker-image-ce` directory (contains both `build/libs/` and `src/main/docker/`)
- The Dockerfile uses `eclipse-temurin:21-jre-alpine` which supports multi-arch

### 3.5 Verify connector image

```bash
# Inspect manifest
docker buildx imagetools inspect ghcr.io/takuma-8trees/edc-ce:latest
```

Expected output should show both `linux/amd64` and `linux/arm64` platforms.
✅ Verified on 2025-11-09: Digest `sha256:ab41eb698f2795700cd426c16e10909cc66d464701efebe4617537e8a9eecd86`

---

## 4. Test images locally

### 4.1 Test frontend (edc-ce-ui)

**Important:** The frontend requires the `NEXT_PUBLIC_MANAGEMENT_API_URL` environment variable to start properly.

**Test on amd64:**
```bash
docker run --rm --platform linux/amd64 -d -p 8082:8080 \
  -e NEXT_PUBLIC_MANAGEMENT_API_URL=http://localhost:11002/api/management \
  --name edc-ui-test-amd64 \
  ghcr.io/takuma-8trees/edc-ce-ui:latest

# Wait a few seconds for startup
sleep 5

# Check if it responds
curl -I http://localhost:8082/

# Verify architecture inside container
docker exec edc-ui-test-amd64 uname -m
# Returns: x86_64

# Cleanup
docker rm -f edc-ui-test-amd64
```

**Test on arm64:**
```bash
docker run --rm --platform linux/arm64 -d -p 8083:8080 \
  -e NEXT_PUBLIC_MANAGEMENT_API_URL=http://localhost:11002/api/management \
  --name edc-ui-test-arm64 \
  ghcr.io/takuma-8trees/edc-ce-ui:latest

# Wait a few seconds for startup
sleep 5

# Check if it responds
curl -I http://localhost:8083/

# Verify architecture inside container
docker exec edc-ui-test-arm64 uname -m
# Returns: aarch64

# Cleanup
docker rm -f edc-ui-test-arm64
```

**Expected result:** 
- HTTP 200 OK from both tests
- X-Powered-By: Next.js header present
- Content-Type: text/html; charset=utf-8
- ✅ Verified working on 2025-11-09

### 4.2 Test connector (edc-ce)

**Important:** The sovity EDC connector requires PostgreSQL database and several configuration properties. For basic architecture verification:

**Verify multi-arch manifest:**
```bash
docker buildx imagetools inspect ghcr.io/takuma-8trees/edc-ce:latest
```

**Expected output:**
```
Manifests:
  Platform:    linux/amd64
  Platform:    linux/arm64
```
✅ Verified on 2025-11-09: Digest `sha256:ab41eb698f2795700cd426c16e10909cc66d464701efebe4617537e8a9eecd86`

**Test image architecture (without full startup):**

```bash
# Test arm64 image
docker run --platform linux/arm64 --entrypoint sh -d --name edc-ce-test-arm64 \
  ghcr.io/takuma-8trees/edc-ce:latest -c "sleep 10"

docker exec edc-ce-test-arm64 uname -m
# Returns: aarch64

docker exec edc-ce-test-arm64 java -version
# Returns: openjdk version "21.0.9" 2025-10-21 LTS

docker rm -f edc-ce-test-arm64
```
✅ Verified on 2025-11-09: ARM64 image runs correctly with Java 21

**Note on amd64 testing on ARM hosts:**
Testing linux/amd64 images on ARM64 hosts requires QEMU emulation. The image builds and pushes correctly for amd64, but may have exec format errors when running on ARM64 hardware without proper QEMU setup.

**Full connector testing with PostgreSQL:**

For complete functional testing, the connector requires:
- PostgreSQL database
- Required environment variables: `sovity.deployment.kind`, `sovity.edc.fqdn.public`, `sovity.edc.fqdn.internal`, `sovity.jdbc.url`, `sovity.jdbc.user`, `sovity.jdbc.password`, `sovity.dataspace.kind`, `sovity.management.api.iam.kind`

See the example configuration in [docs/deployment-guide/goals/local-demo-ce/docker-compose.yaml](https://github.com/sovity/edc-ce/blob/main/docs/deployment-guide/goals/local-demo-ce/docker-compose.yaml) for a complete setup with all required services.

### 4.3 Advanced verification

**Pull specific platform:**
```bash
docker pull --platform=linux/arm64 ghcr.io/takuma-8trees/edc-ce-ui:latest
docker pull --platform=linux/amd64 ghcr.io/takuma-8trees/edc-ce:latest
```

**Inspect image details:**
```bash
docker image inspect ghcr.io/takuma-8trees/edc-ce-ui:latest | grep -A5 Architecture
docker image inspect ghcr.io/takuma-8trees/edc-ce:latest | grep -A5 Architecture
```

**Run with interactive shell (debugging):**
```bash
docker run --rm -it --platform linux/arm64 --entrypoint sh ghcr.io/takuma-8trees/edc-ce:latest
```

---

## 5. Troubleshooting

### Frontend build issues

**Problem:** `yarn: error: no such option: --immutable`
- **Cause:** System yarn is v1 (classic), but project uses v4 (Berry)
- **Solution:** Use bundled yarn: `node .yarn/releases/yarn-4.1.0.cjs install`

**Problem:** `COPY .next/standalone: not found`
- **Cause:** Next.js build wasn't run before docker build
- **Solution:** Run `yarn next:build` first to generate `.next/standalone` and `.next/static`

### Connector build issues

**Problem:** `error: invalid source release: 21`
- **Cause:** System Java version is not JDK 21 (connector requires Java 21)
- **Solution:** Install and configure JDK 21:
  ```bash
  # Ubuntu/Debian
  sudo apt install -y openjdk-21-jdk
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture)
  export PATH=$JAVA_HOME/bin:$PATH
  java -version  # Verify it shows 21.x
  ```
- **Verified on:** 2025-11-09 (build failed with Java 17, succeeded with Java 21)

**Problem:** `Need Gradle Properties 'gpr.user' and 'gpr.key'`
- **Cause:** Build needs GitHub credentials for private Maven packages
- **Solution:** Set `GPR_USER` and `GPR_KEY` environment variables (or `USERNAME`/`TOKEN`)
  ```bash
  export GPR_USER=your-github-username
  export GPR_KEY=your-github-pat
  ```
- **Alternative:** Add to `~/.gradle/gradle.properties`:
  ```properties
  gpr.user=your-github-username
  gpr.key=your-github-pat
  ```

**Problem:** `build/libs/app.jar: not found`
- **Cause:** JAR wasn't built before docker build
- **Solution:** Run `./gradlew :ce:docker-image-ce:build -x test` first

### Docker buildx issues

**Problem:** `multiple platforms feature is currently not supported for docker driver`
- **Cause:** Using default docker driver instead of buildx
- **Solution:** Create and use a buildx builder: `docker buildx create --use`

**Problem:** `exec user process caused: exec format error`
- **Cause:** Trying to run wrong architecture without QEMU
- **Solution:** Install QEMU: `docker run --rm --privileged tonistiigi/binfmt:latest --install all`

---

## 6. Key differences from dataspace-portal build

| Aspect | dataspace-portal | edc-ce |
|--------|------------------|--------|
| Frontend | Angular with yarn v1 | Next.js with yarn v4 |
| Backend | Quarkus (JDK 17) | EDC connector (JDK 21) |
| Build approach | Gradle build inside repo | Gradle build + simple runtime Dockerfile |
| Dependencies | GitHub Packages (some private) | GitHub Packages (requires auth) |
| Base images | eclipse-temurin:17-jre | eclipse-temurin:21-jre-alpine |

---

## 7. CI/CD considerations (GitHub Actions)

For automated builds in GitHub Actions, create `.github/workflows/build-multi-arch.yml`:

```yaml
name: Build multi-arch images

on:
  push:
    branches: [ main ]
  workflow_dispatch:

permissions:
  contents: read
  packages: write

jobs:
  build-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up QEMU
        uses: docker/setup-qemu-action@v2
        with:
          platforms: arm64,amd64
      
      - name: Set up Buildx
        uses: docker/setup-buildx-action@v3
      
      - name: Login to GHCR
        uses: docker/login-action@v2
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build frontend
        working-directory: frontend
        run: |
          node .yarn/releases/yarn-4.1.0.cjs install
          node .yarn/releases/yarn-4.1.0.cjs next:build
      
      - name: Build and push frontend image
        uses: docker/build-push-action@v4
        with:
          context: frontend
          file: frontend/Dockerfile
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/edc-ce-ui:${{ github.sha }}

  build-connector:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Build connector JAR
        working-directory: connector
        env:
          USERNAME: ${{ github.actor }}
          TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: ./gradlew :ce:docker-image-ce:build -x test
      
      - name: Set up QEMU
        uses: docker/setup-qemu-action@v2
      
      - name: Set up Buildx
        uses: docker/setup-buildx-action@v3
      
      - name: Login to GHCR
        uses: docker/login-action@v2
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      
      - name: Build and push connector image
        uses: docker/build-push-action@v4
        with:
          context: connector/ce
          file: connector/ce/docker-image-ce/src/main/docker/Dockerfile
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/edc-ce:${{ github.sha }}
```

---

## Summary

### What worked well
- Using bundled yarn 4.1.0 for frontend builds
- Building artifacts locally before docker build (avoids credential issues in buildx)
- Simple runtime Dockerfiles that only copy pre-built artifacts
- eclipse-temurin base images have good multi-arch support

### Lessons learned
- Always verify `.next/standalone` and `.next/static` exist before building frontend image
- Gradle needs GitHub credentials for private Maven packages - set via env vars or gradle.properties
- Test both platforms locally using `--platform` flag before relying on manifest
- Document exact yarn/node versions to avoid confusion with system-installed versions

### Build times (approximate, with warm cache)
- Frontend artifacts: 30-40 seconds
- Frontend docker build+push: 2 minutes
- Connector JAR: 3-5 minutes (first build ~10 min)
- Connector docker build+push: 2 minutes

### Image sizes
- edc-ce-ui: ~140 MB per platform
- edc-ce: ~200 MB per platform

---

## References
- [Docker Buildx documentation](https://docs.docker.com/buildx/working-with-buildx/)
- [Next.js standalone output](https://nextjs.org/docs/app/api-reference/next-config-js/output)
- [Eclipse Adoptium images](https://hub.docker.com/_/eclipse-temurin)
- [GHCR authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
