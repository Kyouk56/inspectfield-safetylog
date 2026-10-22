package com.safetylog.app.domain

data class ChecklistItemDefinition(
    val index: Int,
    val title: String,
    val standardCode: String,
    val description: String,
    val failureTag: String
)

data class EquipmentChecklist(
    val categoryName: String,
    val standardTitle: String,
    val items: List<ChecklistItemDefinition>,
    val commonDefectTags: List<String>
)

object ChecklistRegistry {

    val allProtocols: List<EquipmentChecklist> by lazy {
        listOf(
            fireExtinguisherChecklist,
            aedChecklist,
            emergencyLightingChecklist,
            eyeWashChecklist,
            fireDoorChecklist,
            generalSafetyChecklist
        )
    }

    fun getChecklistByName(name: String): EquipmentChecklist {
        return allProtocols.find { it.categoryName.equals(name, ignoreCase = true) }
            ?: getChecklistForType(name)
    }

    fun getChecklistForType(equipmentType: String): EquipmentChecklist {
        val lower = equipmentType.lowercase()

        return when {
            lower.contains("aed") || lower.contains("defibrillator") -> aedChecklist
            lower.contains("light") || lower.contains("exit") || lower.contains("egress") -> emergencyLightingChecklist
            lower.contains("eye") || lower.contains("wash") || lower.contains("shower") -> eyeWashChecklist
            lower.contains("door") -> fireDoorChecklist
            lower.contains("extinguisher") || lower.contains("chemical") || lower.contains("co2") || lower.contains("water") -> fireExtinguisherChecklist
            else -> generalSafetyChecklist
        }
    }

    private val fireExtinguisherChecklist = EquipmentChecklist(
        categoryName = "Fire Extinguisher",
        standardTitle = "NFPA 10 Periodic Inspection Standard",
        items = listOf(
            ChecklistItemDefinition(
                index = 1,
                title = "Pressure Gauge Verification",
                standardCode = "NFPA 10 §7.2.2(4)",
                description = "Pressure gauge needle is positioned in the operable green zone.",
                failureTag = "Low / High Gauge Pressure"
            ),
            ChecklistItemDefinition(
                index = 2,
                title = "Discharge Hose & Nozzle",
                standardCode = "NFPA 10 §7.2.2(5)",
                description = "Hose is flexible, unobstructed, firmly connected, with no cracks or rot.",
                failureTag = "Damaged / Blocked Discharge Hose"
            ),
            ChecklistItemDefinition(
                index = 3,
                title = "Pull-Pin & Tamper Seal",
                standardCode = "NFPA 10 §7.2.2(3)",
                description = "Safety pull-pin locked with unbroken inspection tamper seal.",
                failureTag = "Broken Tamper Seal / Missing Pin"
            ),
            ChecklistItemDefinition(
                index = 4,
                title = "Operating Signage & Placard",
                standardCode = "NFPA 10 §7.2.2(2)",
                description = "Operating instructions face outward and are completely legible.",
                failureTag = "Missing / Obscured Operating Signage"
            ),
            ChecklistItemDefinition(
                index = 5,
                title = "Physical Clearance & Mounting",
                standardCode = "NFPA 10 §7.2.2(1)",
                description = "36-inch clearance unobstructed; firmly mounted on wall bracket or cabinet.",
                failureTag = "Blocked Physical Access / Loose Bracket"
            )
        ),
        commonDefectTags = listOf(
            "Low Gauge Pressure",
            "Broken Tamper Seal",
            "Blocked Physical Access",
            "Cracked Discharge Hose",
            "Missing Wall Signage",
            "Dented Cylinder Shell",
            "Hydrostatic Test Overdue"
        )
    )

    private val aedChecklist = EquipmentChecklist(
        categoryName = "Automated External Defibrillator",
        standardTitle = "AHA & OSHA AED Readiness Protocol",
        items = listOf(
            ChecklistItemDefinition(
                index = 1,
                title = "Status Readiness Indicator",
                standardCode = "AHA Guidelines §12",
                description = "Active readiness LED flashes green or active OK checkmark is displayed.",
                failureTag = "AED Readiness Light Flashing Red / Inactive"
            ),
            ChecklistItemDefinition(
                index = 2,
                title = "Electrode Pads Expiration & Seal",
                standardCode = "OSHA 1910.151",
                description = "Adult and pediatric pads are factory sealed, connected, and within expiry date.",
                failureTag = "Electrode Pads Expired or Unsealed"
            ),
            ChecklistItemDefinition(
                index = 3,
                title = "Operational Battery Capacity",
                standardCode = "Manufacturer Spec",
                description = "Lithium battery pack is seated securely with active operational capacity confirmed.",
                failureTag = "Low Battery / Unit Beeping"
            ),
            ChecklistItemDefinition(
                index = 4,
                title = "Emergency Rescue Response Kit",
                standardCode = "AHA Protocol",
                description = "Scissors, prep razor, medical gloves, and CPR barrier mask present in pack.",
                failureTag = "Incomplete Rescue Kit (Missing CPR Mask/Scissors)"
            ),
            ChecklistItemDefinition(
                index = 5,
                title = "Wall Cabinet & Audible Alarm",
                standardCode = "Life-Safety Protocol",
                description = "Cabinet door opens freely, unobstructed, and audible siren triggers cleanly.",
                failureTag = "Cabinet Alarm Defective / Blocked Visibility"
            )
        ),
        commonDefectTags = listOf(
            "Readiness Indicator Failed",
            "Electrode Pads Past Expiry",
            "Low Battery Warning Beep",
            "Missing Rescue Kit Components",
            "Cabinet Alarm Not Sounding",
            "Blocked Access Route"
        )
    )

    private val emergencyLightingChecklist = EquipmentChecklist(
        categoryName = "Emergency & Exit Lighting",
        standardTitle = "NFPA 101 & OSHA 1910.37 Egress Lighting Protocol",
        items = listOf(
            ChecklistItemDefinition(
                index = 1,
                title = "30-Second Push-To-Test",
                standardCode = "NFPA 101 §7.9.3",
                description = "Manual push button pressed; lamps stay fully illuminated for 30 seconds.",
                failureTag = "30-Sec Illumination Test Failed"
            ),
            ChecklistItemDefinition(
                index = 2,
                title = "Battery Charging Circuit & LED",
                standardCode = "NFPA 101 §7.9",
                description = "AC pilot power indicator LED is steadily illuminated in charging mode.",
                failureTag = "AC Charging Indicator Dark"
            ),
            ChecklistItemDefinition(
                index = 3,
                title = "Lamp Head Egress Aiming",
                standardCode = "OSHA 1910.37(b)",
                description = "Dual lamp heads aimed appropriately along exit corridors and walkways.",
                failureTag = "Improper Lamp Alignment / Loose Swivel"
            ),
            ChecklistItemDefinition(
                index = 4,
                title = "Exit Signage & Lettering",
                standardCode = "NFPA 101 §7.10",
                description = "Red/green letters and directional chevrons brightly lit with no burnt bulbs.",
                failureTag = "Burnt Exit Bulbs / Dark Faceplate"
            ),
            ChecklistItemDefinition(
                index = 5,
                title = "Housing Integrity & Mounting",
                standardCode = "UL 924 Standard",
                description = "Enclosure firmly mounted with no cracked diffusers or exposed wiring.",
                failureTag = "Cracked Casing / Exposed Wiring"
            )
        ),
        commonDefectTags = listOf(
            "30-Sec Test Failed",
            "Battery Fails to Hold Load",
            "Burnt Lamp Heads",
            "Dark Exit Sign Lettering",
            "Cracked Diffuser Lens",
            "Disconnected Wiring"
        )
    )

    private val eyeWashChecklist = EquipmentChecklist(
        categoryName = "Emergency Eyewash & Shower",
        standardTitle = "ANSI/ISEA Z358.1 Weekly Verification Standard",
        items = listOf(
            ChecklistItemDefinition(
                index = 1,
                title = "Single-Action Activation Flow",
                standardCode = "ANSI Z358.1 §5.2",
                description = "Water initiates in 1 second or less with a single push paddle motion.",
                failureTag = "Activation Valve Sticking / Slow Flow"
            ),
            ChecklistItemDefinition(
                index = 2,
                title = "Dual Aerated Flushing Streams",
                standardCode = "ANSI Z358.1 §5.1.8",
                description = "Water streams are clean, symmetrical, and adequate to rinse both eyes.",
                failureTag = "Uneven / Weak Flushing Stream"
            ),
            ChecklistItemDefinition(
                index = 3,
                title = "Protective Dust Caps",
                standardCode = "ANSI Z358.1 §5.1.3",
                description = "Flushing nozzle caps pop off automatically with water stream pressure.",
                failureTag = "Missing / Stuck Dust Caps"
            ),
            ChecklistItemDefinition(
                index = 4,
                title = "Continuous Hands-Free Flow",
                standardCode = "ANSI Z358.1 §5.2",
                description = "Stay-open ball valve remains open without requiring operator's hand.",
                failureTag = "Valve Closes Prematurely"
            ),
            ChecklistItemDefinition(
                index = 5,
                title = "Clear 10-Second Pathway",
                standardCode = "ANSI Z358.1 §5.4.2",
                description = "Path unobstructed on same level; inspection tag signed and dated.",
                failureTag = "Pathway Obstructed by Freight"
            )
        ),
        commonDefectTags = listOf(
            "Slow Water Flow",
            "Rusty / Discolored Water",
            "Stuck Protective Nozzle Caps",
            "Stay-Open Valve Faulty",
            "Blocked Travel Corridor",
            "Missing Inspection Record Tag"
        )
    )

    private val fireDoorChecklist = EquipmentChecklist(
        categoryName = "Fire Door Assembly",
        standardTitle = "NFPA 80 Opening Protectives Standard",
        items = listOf(
            ChecklistItemDefinition(
                index = 1,
                title = "Autonomous Self-Closing & Latch",
                standardCode = "NFPA 80 §5.2.3.1",
                description = "Door swings freely and latches securely into strike with no manual push.",
                failureTag = "Door Fails to Latch Autonomously"
            ),
            ChecklistItemDefinition(
                index = 2,
                title = "Unobstructed Egress Operation",
                standardCode = "NFPA 80 §5.2.3.3",
                description = "No wooden wedges, kick-stops, or physical hold-open props attached.",
                failureTag = "Unapproved Hold-Open Wedge in Use"
            ),
            ChecklistItemDefinition(
                index = 3,
                title = "Smoke & Intumescent Seals",
                standardCode = "NFPA 80 §5.2.3.7",
                description = "Perimeter gaskets and astragals continuous and intact without tears.",
                failureTag = "Damaged / Missing Smoke Gasket"
            ),
            ChecklistItemDefinition(
                index = 4,
                title = "Hinges & Panic Hardware",
                standardCode = "NFPA 80 §5.2.3.2",
                description = "Panic exit device, hinges, and coordinator tight with all fasteners secure.",
                failureTag = "Loose Panic Bar / Worn Hinges"
            ),
            ChecklistItemDefinition(
                index = 5,
                title = "Mandatory Warning Signage",
                standardCode = "NFPA 80 §4.1.4",
                description = "Sign 'Fire Door - Do Not Block / Keep Closed' clearly visible on door.",
                failureTag = "Missing Fire Door Placard"
            )
        ),
        commonDefectTags = listOf(
            "Fails to Latch Automatically",
            "Hold-Open Wedge Installed",
            "Torn Smoke Seals",
            "Damaged Closer Mechanism",
            "Missing Regulatory Signage"
        )
    )

    private val generalSafetyChecklist = EquipmentChecklist(
        categoryName = "Commercial Safety Equipment",
        standardTitle = "Life-Safety Commercial Protocol",
        items = listOf(
            ChecklistItemDefinition(
                index = 1,
                title = "Operational Readiness & Status",
                standardCode = "OSHA Standard",
                description = "Unit is in active service, fully functional, and ready for immediate deployment.",
                failureTag = "Device Inactive / Out of Service"
            ),
            ChecklistItemDefinition(
                index = 2,
                title = "Physical Integrity & Casing",
                standardCode = "Manufacturer Spec",
                description = "Casing free of severe impact damage, structural cracks, rust, or leaks.",
                failureTag = "Physical Damage / Corrosion"
            ),
            ChecklistItemDefinition(
                index = 3,
                title = "Safety Labels & Directions",
                standardCode = "OSHA 1910.145",
                description = "Manufacturer ratings, operating steps, and certification decals legible.",
                failureTag = "Faded / Missing Instructions"
            ),
            ChecklistItemDefinition(
                index = 4,
                title = "Physical Clearance & Lighting",
                standardCode = "OSHA 1910.37",
                description = "Location is well-lit, unobstructed, and accessible within immediate reach.",
                failureTag = "Restricted Physical Access"
            ),
            ChecklistItemDefinition(
                index = 5,
                title = "Mounting & Secure Fasteners",
                standardCode = "IBC Safety Code",
                description = "Bracket, enclosure, or floor stand firmly secured with sound anchors.",
                failureTag = "Loose Wall Mounting Bracket"
            )
        ),
        commonDefectTags = listOf(
            "Physical Damage Detected",
            "Obstructed Physical Access",
            "Missing Operating Decals",
            "Loose Mounting Fasteners",
            "Expired Service Interval"
        )
    )
}
