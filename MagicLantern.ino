#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <ESP8266mDNS.h>
#include <WS2812FX.h>
#include <Restfully.h>
#include "configuration.h"

int handleEcho(RestRequest& request);
int handlePowerOn(RestRequest& request);
int handlePowerOff(RestRequest& request);
int handleModeCycle(RestRequest& request);
int handleModeCyclePause(RestRequest& request);
int handleModeFire(RestRequest& request);
int handleModeStatic(RestRequest& request);
int handleModeOff(RestRequest& request);
int handleBrightnessPOST(RestRequest& request);
int handleBrightnessGET(RestRequest& request);
void srv_handle_not_found();

// With gratitude to kitesurfer's webserver example -  https://github.com/kitesurfer1404/WS2812FX/tree/master/examples

// See https://github.com/esp8266/Arduino/issues/263
#define min(a,b) ((a)<(b)?(a):(b))
#define max(a,b) ((a)>(b)?(a):(b))

#define LED_PIN 14
#define LED_COUNT 12

#define WIFI_TIMEOUT 30000
#define HTTP_PORT 80

#define DEFAULT_COLOR 0xFF5900
#define DEFAULT_BRIGHTNESS 255
#define DEFAULT_SPEED 1000
#define DEFAULT_MODE FX_MODE_STATIC

unsigned long cycle_last_change_time = 0;
unsigned long last_wifi_check_time = 0;
boolean cycle_mode_on = false;

WS2812FX neopixel_ring = WS2812FX(LED_COUNT, LED_PIN, NEO_GRBW + NEO_KHZ800);
ESP8266WebServer server(HTTP_PORT);
RestRequestHandler restHandler;

void sendHeaders() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
  server.sendHeader("Access-Control-Allow-Methods", "GET");
}

class OptionsRequestHandler : public RequestHandler {
    virtual bool canHandle(HTTPMethod method, String uri) {
      return method == HTTP_OPTIONS;
    }
    virtual bool handle(ESP8266WebServer& server, HTTPMethod requestMethod, String requestUri) {
      sendHeaders();
      server.send(200, "application/json; charset=utf-8", "");
      return true;
    }
} optionsRequestHandler;

void setup_wifi() {
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(WIFI_SSID);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  WiFi.mode(WIFI_STA);
  #ifdef STATIC_IP
    WiFi.config(ip, gateway, subnet);
  #endif

  unsigned long connect_start = millis();
  while(WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");

    if(millis() - connect_start > WIFI_TIMEOUT) {
      Serial.println();
      Serial.print("Tried ");
      Serial.print(WIFI_TIMEOUT);
      Serial.print("ms. Resetting ESP now.");
      ESP.reset();
    }
  }

  Serial.println("");
  Serial.println("WiFi connected");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
  Serial.println();
}

void setup_neopixel_ring() {
  neopixel_ring.init();
  neopixel_ring.setMode(DEFAULT_MODE);
  neopixel_ring.setColor(DEFAULT_COLOR);
  neopixel_ring.setSpeed(DEFAULT_SPEED);
  neopixel_ring.setBrightness(DEFAULT_BRIGHTNESS);
  neopixel_ring.stop();
}

void setup_http_server() {
  server.addHandler(&optionsRequestHandler);
  server.addHandler(&restHandler);

  server.onNotFound(srv_handle_not_found);

  restHandler.on("/api/brightness/:brightness(integer)", POST(handleBrightnessPOST));
  restHandler.on("/api/brightness", GET(handleBrightnessGET));

  restHandler.on("/api/power/on", POST(handlePowerOn));
  restHandler.on("/api/power/off", POST(handlePowerOff));

  restHandler.on("/api/mode/cycle", POST(handleModeCycle));
  restHandler.on("/api/mode/cycle/pause", POST(handleModeCyclePause));
  restHandler.on("/api/mode/fire", POST(handleModeFire));
  restHandler.on("/api/mode/static", POST(handleModeStatic));
  restHandler.on("/api/mode/off", POST(handleModeOff));
	
  restHandler.on("/api/echo/:msg(string|integer)", GET(handleEcho) );
}

int handleEcho(RestRequest& request) {
  String s("Hello ");
    auto msg = request["msg"];
    if(msg.isString())
      s += msg.toString();
    else {
      s += '#';
      s += (long)msg;
    }
    request.response["reply"] = s;
    return 200;
}

/**  Webserver Functions **/

int handlePowerOn(RestRequest& request) {
  neopixel_ring.start();
  request.response["power"] = "on";
  return 200;
}

int handlePowerOff(RestRequest& request) {
  neopixel_ring.stop();
  request.response["power"] = "off";
  return 200;
}

int handleModeCycle(RestRequest& request) {
  cycle_mode_on = true;
  request.response["cycle"] = "on";
  return 200;
}

int handleModeCyclePause(RestRequest& request) {
  cycle_mode_on = false;
  request.response["cycle"] = "paused";
  return 200;
}

int handleModeFire(RestRequest& request) {
  cycle_mode_on = false;
  neopixel_ring.setMode(FX_MODE_FIRE_FLICKER_INTENSE);
  request.response["mode"] = "fire";
  return 200;
}

int handleModeStatic(RestRequest& request) {
  cycle_mode_on = false;
  request.response["mode"] = "static";
  return 200;
}

int handleModeOff(RestRequest& request) {
  cycle_mode_on = false;
  neopixel_ring.stop();
  request.response["mode"] = "off";
  return 200;
}

int handleBrightnessPOST(RestRequest& request) {
  auto brightness = request["brightness"];
  Serial.print("Lantern set to ");
  Serial.println((long)brightness);
  neopixel_ring.setBrightness((long)brightness);
  request.response["brightness"] = (long)brightness;
  return 200;
}

int handleBrightnessGET(RestRequest& request) {
  Serial.println("brightness get");
  request.response["brightness"] = neopixel_ring.getBrightness();
  return 200;
}

void srv_handle_not_found() {
  String path = server.uri();
  Serial.print("URI not found: ");
  Serial.println(path);
  server.send(404, "text/plain", "File Not Found");
}

void setup(){
  Serial.begin(115200);
  Serial.println("Starting...");

  Serial.println("NeoPixel Ring initializing...");
  setup_neopixel_ring();

  Serial.println("Wifi initializing...");
  setup_wifi();

  Serial.println("HTTP server initializing...");
  setup_http_server();

  server.begin();
  Serial.println("HTTP server started.");

  if (!MDNS.begin(HOSTNAME)) {
    Serial.println("Error setting up MDNS responder!");
  }
  Serial.print("mDNS responder started as "); Serial.print(HOSTNAME); Serial.println(".local");

  Serial.println("ready!");
}

void loop() {
  unsigned long now = millis();

  server.handleClient();
  neopixel_ring.service();

  if(now - last_wifi_check_time > WIFI_TIMEOUT) {
    Serial.print("Checking WiFi... ");
    if(WiFi.status() != WL_CONNECTED) {
      Serial.println("WiFi connection lost. Reconnecting...");
      setup_wifi();
    } else {
      Serial.println("OK");
    }
    last_wifi_check_time = now;
  }

  if(cycle_mode_on && (now - cycle_last_change_time > 10000)) {
    uint8_t next_mode = (neopixel_ring.getMode() + 1) % neopixel_ring.getModeCount();
    neopixel_ring.setMode(next_mode);
    Serial.print("mode is "); Serial.println(neopixel_ring.getModeName(neopixel_ring.getMode()));
    cycle_last_change_time = now;
  }
}
