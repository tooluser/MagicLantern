definition(
	name: "Magic Lantern",
	namespace: "Nowhereville",
	author: "Joshua Marker",
	description: "A little magic in your life.",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "")

preferences {
	section("Lantern") {
		input "deviceIP", "text", title: "Lantern IP Address", description: "Device IP (e.g. 10.0.1.X)", required: true, defaultValue: "10.0.1.X"
	}
	section("Heating Sensors") {
		input("temperatureSensor", "capability.temperatureMeasurement",
			title: "Temperature Sensor", multiple: false, required: true)
		input("heaterSwitch", "capability.switch",
			title: "Heater Active", multiple: false, required: true)
		input("heatedTemperatureThreshold", "integer", title: "Heated cutoff", description: "Temperature at which to consider heating complete",
			multiple: false, required: true, defaultValue: 100)
	}
}

def installed() {
	log.debug "installed"
	initialize()
	subscribe()
}

def updated() {
	log.debug "updated"
	initialize()
	unsubscribe()
	subscribe()
}

private def subscribe() {
	log.debug "subscribed"
	subscribe(temperatureSensor, "temperature", temperatureDidChangeHandler)
	subscribe(heaterSwitch, "switch", heaterSwitchDidChangeHandler)
}

def initialize() {
	log.debug "initialize"
	log.debug "Temperature Sensor Linked: " + temperatureSensor
	log.debug "Heater Switch Linked: " + heaterSwitch
	createChildDevices()
	getChildDevice()

	// Push configuration to child
	lanternDevice().setIPAddress(deviceIP)
}

def uninstalled() {
	devices = getChildDevices()
	devices.each {device ->
		log.debug "Deleting ${device} - ${device.deviceNetworkId}"
		deleteChildDevice(device.deviceNetworkId)
	}

	log.debug "uninstalled"
}

private createChildDevices() {
	try {
		addChildDevice(
			"InovelliUSA",
			"Switch Child Device",
			powerSwitchDNI(),
			null,
			[
				isComponent: true,
				name: "Magic Lantern - On/Off Switch",
				label: "Magic Lantern Power",
				completedSetup: true
			]
		)

		addChildDevice(
			"InovelliUSA",
			"Switch Child Device",
			fireModeSwitchDNI(),
			null,
			[
				isComponent: true,
				name: "Magic Lantern - Fire Mode",
				label: "Magic Lantern Fire Mode",
				completedSetup: true
			]
		)

		addChildDevice(
			"InovelliUSA",
			"Switch Child Device",
			temperatureModeSwitchDNI(),
			null,
			[
				isComponent: true,
				name: "Magic Lantern - Temperature Mode",
				label: "Magic Lantern Temperature Mode",
				completedSetup: true
			]
		)

		addChildDevice(
			"Nowhereville",
			"Lantern",
			lanternDeviceDNI(),
			null,
			[
				isComponent: true,
				name: "Magic Lantern - Lantern Device",
				label: "Magic Lantern Device",
				completedSetup: true
			]
		)
	} catch (e) {
		log.error "Child device creation failed with error = ${e}"
	}

	childDevices = getChildDevices()
	log.debug "Child Devices: ${childDevices}"
}

def temperatureDidChangeHandler(evt) {
	log.debug("+++++ temperatureDidChangeHandler ${evt} - ${evt.name} - ${evt.value}")
	if (temperatureModeOn()) {
		if (evt.value >= 80 && evt.value < heatedTemperatureThreshold) {
			lanternDevice().setBrightness(brightnessForTemperature(evt.value))
		} else if (evt.value > heatedTemperatureThreshold) {
			lanternDevice().setModePulse()
			lanternDevice().setBrightness(100)
		} else {
			// Cold.
			lanternDevice().setBrightness(23)
			lanternDevice().setModeStatic()
		}
	}
}

def heaterSwitchDidChangeHandler(evt) {
	log.debug("+++++ heaterSwitchDidChangeHandler ${evt} - ${evt.name} - ${evt.value}")
	if (temperatureModeOn()) {
		if (evt.value == "on") {
			lanternDevice().setModeFire()
			lanternDevice().setBrightness(100)
		}
	}
}

def List childOn(dni)  {
	logDebug("childOn dni=${dni}")
	switch (dni) {
		case powerSwitchDNI():
			lanternDevice().on()
			break
		case fireModeSwitchDNI():
			// Need to delay these? Perhaps.
			lanternDevice().setBrightness(100)
			lanternDevice().setModeFire()
			lanternDevice().on()

			temperatureModeSwitch.off()
			break
		case temperatureModeSwitchDNI():
			fireModeSwitch().off() // Yes this is generalizable. Shoot me.
			lanternDevice().on()
			// need to setBrightnessForTemperature here (which may be off)
			break
		default:
			break
	}
}

def List childOff(dni)  {
	logDebug("childOff dni=${dni}")
	switch (dni) {
		case powerSwitchDNI():
			lanternDevice().off();
			break
		default: // All other cases are just mutually-exclusive modal switches turning off due to something else turning on
			break
	}
}

private temperatureModeOn() {
	temperatureModeSwitch().currentValue("switch") == "on"
}

private brightnessForTemperature(temperature) {
	temperature
}

private powerSwitchDNI() { "MagicLantern${app.id}-sw1" }
private powerSwitch() { getChildDevice(powerSwitchDNI()) }

private fireModeSwitchDNI() { "MagicLantern${app.id}-sw2" }
private fireModeSwitch() { getChildDevice(fireModeSwitchDNI()) }

private temperatureModeSwitchDNI() { "MagicLantern${app.id}-sw3" }
private temperatureModeSwitch() { getChildDevice(temperatureModeSwitchDNI()) }

private lanternDeviceDNI() { "MagicLantern${app.id}-lantern" }
private lanternDevice() { getChildDevice(lanternDeviceDNI()) }
