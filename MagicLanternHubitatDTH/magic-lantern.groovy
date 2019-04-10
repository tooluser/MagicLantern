/**
 *  MagicLantern
 *
 *  Author:
 *    Joshua Marker - joshua@nowhereville.org - @tooluser
 *
 */

import hubitat.helper.HexUtils
import hubitat.device.HubAction
import hubitat.helper.InterfaceUtils
import hubitat.device.Protocol

metadata {
	definition (
		name: "Lantern",
		namespace: "Nowhereville",
		author: "Joshua Marker") {

		capability "Actuator"
		capability "Switch"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Initialize"

		command "setBrightness",            [ "number" ] // 0 - 255
		command "setModePulse"
		command "setModeStatic"
		command "setModeCycle"
		command "setModeFire"
	}

	preferences {
		input "deviceIP", "text", title: "Server", description: "Device IP (e.g. 192.168.1.X)", required: true, defaultValue: "10.0.1.X"

		input(name:"logDebug", type:"bool", title: "Log debug information?",
			description: "Logs raw data for debugging. (Default: On)", defaultValue: true,
			required: false, displayDuringSetup: true)

		// input(name:"powerOnWithChanges", type:"bool", title: "Turn on this light when values change?",
		// 	defaultValue: true, required: true, displayDuringSetup: true)
		// input(name:"wwHue", type:"number", title: "Hue that Warm White (orangeish light) uses",
		// 	description: "Hue (0 - 100). Default 100 (Bulb's White LEDs)", defaultValue: 100)
	}
}

def on() {
	sendEvent(name: "switch", value: "on")
	logDebug( "MagicLantern set to on" )
	sendPost("/api/power/on", [power: "on"])
	[]
}

def off() {
	sendEvent(name: "switch", value: "off")
	logDebug( "MagicLantern set to off" )
	sendPost("/api/power/off", [power: "off"])
	[]
}

def setBrightness(Number brightness) {
	if (brightness < 0 || brightness > 255) {
		return
	}

	sendEvent(name: "brightness", value: brightness)
	sendPost("/api/brightness/${brightness}")
	[]
}

def setModePulse() {
	setLanternMode("pulse")
}

def setModeStatic() {
	setLanternMode("static")
}

def setModeCycle() {
	sendEvent(name: "autocycle", value: "on")
	logDebug( "MagicLantern set to autocycle" )
	sendPost("/api/mode/cycle", [mode: "cycle"])
}

def setModeFire() {
	setLanternMode("fire")
}

private setLanternMode(mode) {
	sendEvent(name: "mode", value: mode)
	logDebug( "MagicLantern set to ${mode}" )
	sendPost("/api/mode/${mode}", [mode: mode])
}

private def sendPost(String path, Map bodyMap = [:]) {
	// sendHTTPPost(path, bodyMap)
	sendAsyncHTTPPost(path, bodyMap)
}

private def sendHTTPPost(String path, Map bodyMap = [:]) {
	def staticParams = [
		uri: "http://${settings.deviceIP}${path}",
		contentType: "application/json",
		headers: [contentType: "application/json", requestContentType: "application/x-www-form-urlencoded"],
		body: bodyMap
	]

	postParams = staticParams + [body: bodyMap]

	try {
		httpPost(postParams) { resp ->
			log.debug resp.data
		}
	} catch (Exception e) {
		log.debug "error occured calling httpPost ${e}"
	}
}

private def sendAsyncHTTPPost(String path, Map bodyMap = [:]) {
	def staticParams = [
		uri: "http://${settings.deviceIP}${path}",
		requestContentType: 'application/json',
		contentType: 'application/json',
	]

	postParams = staticParams + [body: bodyMap]

	logDebug "POSTing: to ${postParams['uri']} with ${postParams['body']}"
	asynchttpPost('postCallbackMethod', postParams)
}

def postCallbackMethod(response, data) {
	log.debug("----- postCallbackMethod")
	// Parse returned data, use to sendEvent eg {power: off} -> 	sendEvent(name: "switch", value: "off")
	logDebug("----- postCallbackMethod ${response} - ${data}")
	logDebug "Callback: ${response.status} - ${response.errorData}"
}

def parse (response) {
	logDebug "Device responded with ${response}"
}
//
private logDebug( debugText ){
	if (settings.logDebug) {
		log.info "MagicLantern (${settings.deviceIP}): ${debugText}"
	}
}

def refresh( ) {
	logDebug("+++++ refresh")
}

def poll() {
	logDebug("+++++ poll")
	refresh()
}

def updated() {
	logDebug("+++++ updated")
	initialize()
}

def initialize() {
	logDebug("+++++ initialize")
}

def setIPAddress(ipAddress) {
	log.debug("+++++ set IP address to ${ipAddress}")
	settings.deviceIP = ipAddress
}