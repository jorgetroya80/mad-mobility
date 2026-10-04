package io.github.jorgetroya80.madmobility.shared

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

// Shared infrastructure kernel: open so domain modules can use its sub-packages (emt, web)
@ApplicationModule(type = ApplicationModule.Type.OPEN)
@PackageInfo
class ModuleMetadata
