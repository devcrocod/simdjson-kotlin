import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import javax.inject.Inject

val hostOs: String = when {
    System.getProperty("os.name").lowercase().contains("mac") -> OperatingSystemFamily.MACOS
    System.getProperty("os.name").lowercase().contains("linux") -> OperatingSystemFamily.LINUX
    System.getProperty("os.name").lowercase().contains("win") -> OperatingSystemFamily.WINDOWS
    else -> error("Unsupported OS: ${System.getProperty("os.name")}")
}

val hostArch: String = when (System.getProperty("os.arch")) {
    "aarch64", "arm64" -> MachineArchitecture.ARM64
    "amd64", "x86_64" -> MachineArchitecture.X86_64
    else -> error("Unsupported arch: ${System.getProperty("os.arch")}")
}

dependencies.attributesSchema {
    attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE) {
        disambiguationRules.add(HostOsDisambiguationRule::class.java) {
            params(hostOs)
        }
    }
    attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE) {
        disambiguationRules.add(HostArchDisambiguationRule::class.java) {
            params(hostArch)
        }
    }
}

abstract class HostOsDisambiguationRule @Inject constructor(
    private val hostOs: String
) : AttributeDisambiguationRule<OperatingSystemFamily> {
    override fun execute(details: MultipleCandidatesDetails<OperatingSystemFamily>) {
        if (details.consumerValue == null) {
            details.candidateValues.find { it.name == hostOs }?.let {
                details.closestMatch(it)
            }
        }
    }
}

abstract class HostArchDisambiguationRule @Inject constructor(
    private val hostArch: String
) : AttributeDisambiguationRule<MachineArchitecture> {
    override fun execute(details: MultipleCandidatesDetails<MachineArchitecture>) {
        if (details.consumerValue == null) {
            details.candidateValues.find { it.name == hostArch }?.let {
                details.closestMatch(it)
            }
        }
    }
}
