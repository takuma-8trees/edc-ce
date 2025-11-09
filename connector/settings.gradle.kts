/*
 * Copyright sovity GmbH and/or licensed to sovity GmbH under one or
 * more contributor license agreements. You may not use this file except
 * in compliance with the "Elastic License 2.0".
 *
 * SPDX-License-Identifier: Elastic-2.0
 */

// Enable automatic JDK toolchain download via Foojay resolver
plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "connector"

include(":ce:connector-ce")
include(":ce:docker-image-ce")
include(":ce:libs:api")
include(":ce:libs:api-clients:java-client")
include(":ce:libs:api-clients:typescript-client")
include(":ce:libs:jsonld-lib")
include(":ce:libs:mappers-lib")
include(":ce:libs:runtime-lib")
include(":ce:libs:runtime-os-lib")
include(":ce:libs:runtime-test-lib")
include(":ce:utils:db-schema-ce")
include(":ce:utils:dependency-bundles-ce")
include(":ce:utils:versions-ce")
