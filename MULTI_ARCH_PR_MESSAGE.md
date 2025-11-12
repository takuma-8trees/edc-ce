# Multi-Architecture Docker Image Support (ARM64 + AMD64)

## Summary

This PR adds ARM64 architecture support to the EDC-CE Docker images (both connector and UI), enabling deployment on ARM-based systems such as Apple Silicon Macs, AWS Graviton, and Raspberry Pi.

## Changes Made

### Modified Files
- `.github/workflows/ci.yml`: Added `build-arm64-images` job to verify ARM64 build compatibility

### What the new job does
1. Builds the Connector JAR using Gradle
2. Builds the Next.js frontend application
3. Sets up QEMU for ARM64 emulation
4. Builds Docker images for ARM64 platform (both connector and frontend)
5. Verifies that images can be built successfully without pushing to registry

## Testing

I have verified in my fork repository that:
- ✅ All existing Gradle tests pass
- ✅ ARM64 Docker images build successfully for both connector and frontend
- ✅ No existing CI jobs are affected
- ✅ The new job completes within reasonable time limits

**Test run in my fork:** https://github.com/takuma-8trees/edc-ce/actions

## Technical Details

### Docker Base Images
Both base images already support multi-architecture:
- **Connector**: `eclipse-temurin:21-jre-alpine` (supports AMD64 and ARM64)
- **Frontend**: `node:18-alpine` (supports AMD64 and ARM64)

### Build Approach
- Uses Docker Buildx with QEMU for cross-platform builds
- Builds ARM64 images in CI for verification only (no push)
- Does not modify existing AMD64 build process
- No changes to application code required

### Performance Considerations
- ARM64 build adds approximately 15-20 minutes to CI runtime
- Runs in parallel with other jobs to minimize total pipeline time
- Only builds for verification; actual multi-arch publishing can be added separately if needed

## Future Considerations

If this approach is approved, follow-up work could include:
1. Publishing multi-arch images to GHCR (currently only builds for verification)
2. Adding multi-arch support to release workflow
3. Testing on actual ARM64 hardware (AWS Graviton, Apple Silicon)

## Questions

I have successfully verified this in my fork repository. Would you like me to create a PR to the main repository?

---

## References

- Multi-arch build procedure documentation: `edc-ce-multi-arch-build-procedure.md`
- Docker Buildx documentation: https://docs.docker.com/build/building/multi-platform/
