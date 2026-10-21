package com.arpcbuilder.data

import com.arpcbuilder.domain.model.ComponentType
import com.arpcbuilder.domain.model.Dimension
import com.arpcbuilder.domain.model.FormFactor
import com.arpcbuilder.domain.model.MemoryType
import com.arpcbuilder.domain.model.PCComponent
import com.arpcbuilder.domain.model.SocketLayout
import com.arpcbuilder.domain.model.SocketType
import com.arpcbuilder.domain.model.Vec3
import com.arpcbuilder.domain.repository.ComponentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bundled sample catalogue. Specs are realistic approximations, good enough for teaching
 * compatibility. This is a learning tool, so the catalogue deliberately has no prices.
 *
 * Drop a matching `assets/models/<id>.glb` into the app to replace a part's placeholder box.
 */
@Singleton
class InMemoryComponentRepository @Inject constructor() : ComponentRepository {

    private val catalog = MutableStateFlow(SampleCatalog.all)

    override fun observeCatalog(): Flow<List<PCComponent>> = catalog

    override fun observeByType(type: ComponentType): Flow<List<PCComponent>> =
        catalog.map { list -> list.filter { it.type == type } }
}

object SampleCatalog {
    private fun model(id: String) = "models/$id.glb"

    // ---------- Cases (CASE frame origin = floor centre) ----------
    val lancool216 = PCComponent(
        id = "case_lancool_216", type = ComponentType.CASE, name = "Lian Li Lancool 216",
        modelUrl = model("case_lancool_216"),
        formFactor = FormFactor.E_ATX, dimension = Dimension(0.235f, 0.49f, 0.48f),
        maxGpuLengthMm = 392, maxCoolerHeightMm = 180, hasRgb = true,
    )
    val nr200p = PCComponent(
        id = "case_nr200p", type = ComponentType.CASE, name = "Cooler Master NR200P",
        modelUrl = model("case_nr200p"),
        formFactor = FormFactor.MINI_ITX, dimension = Dimension(0.185f, 0.29f, 0.376f),
        maxGpuLengthMm = 330, maxCoolerHeightMm = 155,
    )

    // ---------- Motherboards (mounted in CASE frame; component side faces +X) ----------
    private val boardOffset = Vec3(x = -0.09f, y = 0.27f, z = 0.02f)
    val z790 = PCComponent(
        id = "mb_msi_z790p", type = ComponentType.MOTHERBOARD, name = "MSI PRO Z790-P WIFI",
        modelUrl = model("mb_msi_z790p"), wattage = 35,
        socketType = SocketType.LGA1700, formFactor = FormFactor.ATX, memoryType = MemoryType.DDR5,
        dimension = Dimension(0.004f, 0.305f, 0.244f), snapPositionOffset = boardOffset,
        ramSlots = 4, m2Slots = 4,
    )
    val b650 = PCComponent(
        id = "mb_asus_b650a", type = ComponentType.MOTHERBOARD, name = "ASUS ROG Strix B650-A",
        modelUrl = model("mb_asus_b650a"), wattage = 35,
        socketType = SocketType.AM5, formFactor = FormFactor.ATX, memoryType = MemoryType.DDR5,
        dimension = Dimension(0.004f, 0.305f, 0.244f), snapPositionOffset = boardOffset,
        ramSlots = 4, m2Slots = 3, hasRgb = true,
    )
    val b550m = PCComponent(
        id = "mb_gigabyte_b550m", type = ComponentType.MOTHERBOARD, name = "Gigabyte B550M DS3H",
        modelUrl = model("mb_gigabyte_b550m"), wattage = 30,
        socketType = SocketType.AM4, formFactor = FormFactor.MICRO_ATX, memoryType = MemoryType.DDR4,
        dimension = Dimension(0.004f, 0.244f, 0.244f), snapPositionOffset = boardOffset,
        ramSlots = 4, m2Slots = 2,
    )

    // ---------- CPUs (MOTHERBOARD frame) ----------
    private val cpuOffset = Vec3(x = 0.004f, y = 0.06f, z = -0.03f)
    private val cpuSize = Dimension(0.004f, 0.045f, 0.0375f)
    val i5 = PCComponent(
        id = "cpu_i5_14600k", type = ComponentType.CPU, name = "Intel Core i5-14600K",
        modelUrl = model("cpu_intel"), wattage = 181,
        socketType = SocketType.LGA1700, dimension = cpuSize, snapPositionOffset = cpuOffset,
    )
    val i9 = PCComponent(
        id = "cpu_i9_14900k", type = ComponentType.CPU, name = "Intel Core i9-14900K",
        modelUrl = model("cpu_intel"), wattage = 253,
        socketType = SocketType.LGA1700, dimension = cpuSize, snapPositionOffset = cpuOffset,
    )
    val r7 = PCComponent(
        id = "cpu_r7_7800x3d", type = ComponentType.CPU, name = "AMD Ryzen 7 7800X3D",
        modelUrl = model("cpu_amd"), wattage = 120,
        socketType = SocketType.AM5, dimension = Dimension(0.004f, 0.04f, 0.04f), snapPositionOffset = cpuOffset,
    )
    val r5 = PCComponent(
        id = "cpu_r5_5600x", type = ComponentType.CPU, name = "AMD Ryzen 5 5600X",
        modelUrl = model("cpu_amd"), wattage = 76,
        socketType = SocketType.AM4, dimension = Dimension(0.004f, 0.04f, 0.04f), snapPositionOffset = cpuOffset,
    )

    // ---------- RAM (MOTHERBOARD frame, slot 0; further slots step along Z) ----------
    private val ramOffset = Vec3(x = 0.018f, y = 0.06f, z = 0.04f)
    private val ramSize = Dimension(0.032f, 0.133f, 0.007f)
    val ddr5Rgb = PCComponent(
        id = "ram_corsair_ddr5", type = ComponentType.RAM, name = "Corsair Vengeance RGB DDR5-6000 16 GB",
        modelUrl = model("ram_stick"), wattage = 5, memoryType = MemoryType.DDR5,
        dimension = ramSize, snapPositionOffset = ramOffset, hasRgb = true,
    )
    val ddr4 = PCComponent(
        id = "ram_kingston_ddr4", type = ComponentType.RAM, name = "Kingston Fury Beast DDR4-3200 16 GB",
        modelUrl = model("ram_stick"), wattage = 4, memoryType = MemoryType.DDR4,
        dimension = ramSize, snapPositionOffset = ramOffset,
    )

    // ---------- NVMe (MOTHERBOARD frame, slot 0; further slots step down) ----------
    private val ssdOffset = Vec3(x = 0.003f, y = -0.01f, z = -0.03f)
    private val ssdSize = Dimension(0.003f, 0.022f, 0.08f)
    val samsung990 = PCComponent(
        id = "ssd_990pro", type = ComponentType.SSD, name = "Samsung 990 Pro 2 TB",
        modelUrl = model("ssd_m2"), wattage = 8, dimension = ssdSize, snapPositionOffset = ssdOffset,
    )
    val sn770 = PCComponent(
        id = "ssd_sn770", type = ComponentType.SSD, name = "WD Black SN770 1 TB",
        modelUrl = model("ssd_m2"), wattage = 6, dimension = ssdSize, snapPositionOffset = ssdOffset,
    )

    // ---------- Coolers (MOTHERBOARD frame, sits on the CPU) ----------
    private val coolerOffset = Vec3(x = 0.085f, y = 0.06f, z = -0.03f)
    val nhD15 = PCComponent(
        id = "cooler_nhd15", type = ComponentType.COOLER, name = "Noctua NH-D15",
        modelUrl = model("cooler_tower"), wattage = 6,
        dimension = Dimension(0.165f, 0.15f, 0.16f), snapPositionOffset = coolerOffset,
        lengthMm = 165, coolingCapacityW = 250,
        supportedSockets = setOf(SocketType.LGA1700, SocketType.LGA1851, SocketType.AM4, SocketType.AM5),
    )
    val peerless = PCComponent(
        id = "cooler_pa120", type = ComponentType.COOLER, name = "Thermalright Peerless Assassin 120 ARGB",
        modelUrl = model("cooler_tower"), wattage = 5,
        dimension = Dimension(0.155f, 0.125f, 0.11f), snapPositionOffset = coolerOffset,
        lengthMm = 155, coolingCapacityW = 245, hasRgb = true,
        supportedSockets = setOf(SocketType.LGA1700, SocketType.AM4, SocketType.AM5),
    )
    val laminar = PCComponent(
        id = "cooler_laminar", type = ComponentType.COOLER, name = "Intel Laminar RM1 (stock)",
        modelUrl = model("cooler_stock"), wattage = 3,
        dimension = Dimension(0.065f, 0.1f, 0.1f), snapPositionOffset = Vec3(x = 0.037f, y = 0.06f, z = -0.03f),
        lengthMm = 65, coolingCapacityW = 95, supportedSockets = setOf(SocketType.LGA1700),
    )

    // ---------- GPUs (MOTHERBOARD frame, top PCIe x16 slot) ----------
    private val gpuOffset = SocketLayout.PCIE_X16
    val rtx4070s = PCComponent(
        id = "gpu_rtx4070s", type = ComponentType.GPU, name = "NVIDIA RTX 4070 SUPER",
        modelUrl = model("gpu_dual_slot"), wattage = 220,
        dimension = Dimension(0.11f, 0.04f, 0.244f), snapPositionOffset = gpuOffset, lengthMm = 244,
    )
    val rtx4090 = PCComponent(
        id = "gpu_rtx4090", type = ComponentType.GPU, name = "NVIDIA RTX 4090",
        modelUrl = model("gpu_quad_slot"), wattage = 450,
        dimension = Dimension(0.15f, 0.075f, 0.357f), snapPositionOffset = gpuOffset, lengthMm = 357, hasRgb = true,
    )
    val rx7800xt = PCComponent(
        id = "gpu_rx7800xt", type = ComponentType.GPU, name = "AMD Radeon RX 7800 XT",
        modelUrl = model("gpu_dual_slot"), wattage = 263,
        dimension = Dimension(0.12f, 0.05f, 0.267f), snapPositionOffset = gpuOffset, lengthMm = 267, hasRgb = true,
    )

    // ---------- PSUs (CASE frame, bottom rear) ----------
    private val psuOffset = Vec3(x = 0.0f, y = 0.047f, z = 0.14f)
    private val psuSize = Dimension(0.15f, 0.086f, 0.14f)
    val rm650 = PCComponent(
        id = "psu_rm650e", type = ComponentType.PSU, name = "Corsair RM650e (650 W)",
        modelUrl = model("psu_atx"), wattage = 650, dimension = psuSize, snapPositionOffset = psuOffset,
    )
    val rm850 = PCComponent(
        id = "psu_rm850x", type = ComponentType.PSU, name = "Corsair RM850x (850 W)",
        modelUrl = model("psu_atx"), wattage = 850, dimension = psuSize, snapPositionOffset = psuOffset,
    )
    val focus1000 = PCComponent(
        id = "psu_focus1000", type = ComponentType.PSU, name = "Seasonic Focus GX-1000 (1000 W)",
        modelUrl = model("psu_atx"), wattage = 1000, dimension = psuSize, snapPositionOffset = psuOffset,
    )

    val all: List<PCComponent> = listOf(
        lancool216, nr200p,
        z790, b650, b550m,
        i5, i9, r7, r5,
        ddr5Rgb, ddr4,
        samsung990, sn770,
        nhD15, peerless, laminar,
        rtx4070s, rtx4090, rx7800xt,
        rm650, rm850, focus1000,
    )
}
