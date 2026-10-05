package io.github.jorgetroya80.madmobility.modules.bicimad

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

// BiciMAD stations domain module: depends only on the shared kernel
@ApplicationModule(id = "bicimad", allowedDependencies = ["shared"])
@PackageInfo
class ModuleMetadata
