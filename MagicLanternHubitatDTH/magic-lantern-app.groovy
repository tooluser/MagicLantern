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
		if(!powerSwitch()) {
			addChildDevice(
				"InovelliUSA",
				"Switch Child Device",
				powerSwitchDNI(),
				null,
				[
					isComponent   : true,
					name          : "Magic Lantern - On/Off Switch",
					label         : "Magic Lantern Power",
					completedSetup: true
				]
			)
		}

		if(!fireModeSwitch()) {
			addChildDevice(
				"InovelliUSA",
				"Switch Child Device",
				fireModeSwitchDNI(),
				null,
				[
					isComponent   : true,
					name          : "Magic Lantern - Fire Mode",
					label         : "Magic Lantern Fire Mode",
					completedSetup: true
				]
			)
		}

		if(!temperatureModeSwitch()) {
			addChildDevice(
				"InovelliUSA",
				"Switch Child Device",
				temperatureModeSwitchDNI(),
				null,
				[
					isComponent   : true,
					name          : "Magic Lantern - Temperature Mode",
					label         : "Magic Lantern Temperature Mode",
					completedSetup: true
				]
			)
		}

		if(!lanternDevice()) {
			addChildDevice(
				"Nowhereville",
				"Lantern",
				lanternDeviceDNI(),
				null,
				[
					isComponent   : true,
					name          : "Magic Lantern - Lantern Device",
					label         : "Magic Lantern Device",
					completedSetup: true
				]
			)
		}
	} catch (e) {
		log.error "Child device creation failed with error = ${e}"
	}

	childDevices = getChildDevices()
	log.debug "Child Devices: ${childDevices}"
}

def temperatureDidChangeHandler(evt) {
	log.debug("+++++ temperatureDidChangeHandler ${evt} - ${evt.name} - ${evt.value}")
	state.temperature = evt.value as Integer
	if (temperatureModeOn()) {
		configureForTemperatureMode()
	}
}

def heaterSwitchDidChangeHandler(evt) {
	log.debug("+++++ heaterSwitchDidChangeHandler ${evt} - ${evt.name} - ${evt.value}")
	state.heaterSwitchState = evt.value
	if (temperatureModeOn()) {
		if (evt.value == "on") {
			lanternDevice().setModeFire()
			configureForTemperatureMode()
		}
	}
}

private configureForTemperatureMode() {
	log.debug("[Configuring for temperature ${state.temperature} against threshold of ${heatedTemperatureThreshold}]")

	Integer threshold = heatedTemperatureThreshold as Integer
	if (state.temperature > threshold) {
		log.debug(" - [Fully heated, pulse]")
		lanternDevice().setModePulse()
		lanternDevice().setBrightness(100)
	} else if (state.temperature >= 50 && state.temperature < threshold) {
		if (heaterSwitchOn()) {
			log.debug(" - [Middle mode, heating - fire]")
			lanternDevice().setModeFire()
		} else {
			log.debug(" - [Middle mode, not heating - brightness]")
			lantern.setModeStatic()
		}
		lanternDevice().setBrightness(brightnessForTemperature(state.temperature))
	} else {
		log.debug(" - [Cold]")
		lanternDevice().setBrightness(23)
		lanternDevice().setModeStatic()
	}
}

def List childOn(dni)  {
	log.debug("childOn dni=${dni}")
	switch (dni) {
		case powerSwitchDNI():
			lanternDevice().on()
			break
		case fireModeSwitchDNI():
			state.mode = "fire"
			// Need to delay these? Perhaps.
			lanternDevice().setBrightness(100)
			lanternDevice().setModeFire()
			lanternDevice().on()
			temperatureModeSwitch.off()
			break
		case temperatureModeSwitchDNI():
			state.mode = "temperature"
			fireModeSwitch().off() // Yes this is generalizable. Shoot me.
			lanternDevice().on()
			// need to setBrightnessForTemperature here (which may be off)
			break
		default:
			break
	}
}

def List childOff(dni)  {
	log.debug("childOff dni=${dni}")
	switch (dni) {
		case powerSwitchDNI():
			lanternDevice().off();
			break
		default: // All other cases are just mutually-exclusive modal switches turning off due to something else turning on
			break
	}
}

private temperatureModeOn() {
	state.mode == "temperature"
}

private heaterSwitchOn() {
	state.heaterSwitchState == "on"
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
