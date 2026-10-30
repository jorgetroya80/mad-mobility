package io.github.jorgetroya80.madtransit.modules.bus

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

// EMT bus stops domain module: depends only on the shared kernel
@ApplicationModule(id = "bus", allowedDependencies = ["shared"])
@PackageInfo
class ModuleMetadata
