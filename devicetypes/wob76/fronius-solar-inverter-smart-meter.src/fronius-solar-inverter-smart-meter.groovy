/**
 *  Fronius Solar Inverter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 */
 
import groovy.json.JsonSlurper
 
preferences {
	input("inverterNumber", "number", title: "Inverter Number", description: "The Inverter Number", required: true, displayDuringSetup: true)
    input("destIp", "text", title: "IP", description: "Inverter Local IP Address", required: true, displayDuringSetup: true)
    input("destPort", "number", title: "Port", description: "TCP Port", required: true, displayDuringSetup: true)
    input("pollingInterval", "number", title:"Polling Interval (min)", defaultValue:"5", range: "2..59", required: true, displayDuringSetup: true)
}

metadata {
	definition (name: "Fronius Solar Inverter & Smart Meter", namespace: "Wob76", author: "Beau Dwyer") {
		capability "Polling"
        capability "Power Meter"
        capability "Energy Meter"
        
        attribute "power_details", "string"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
        
        multiAttributeTile(name:"solar", type:"generic", width:6, height:4) {
            tileAttribute("device.solar_power", key: "PRIMARY_CONTROL") {
                attributeState "power", label:'${currentValue}W', unit: "W", defaultState: true, backgroundColors:[
                    [value: 0, color: "#153591"],
                    [value: 5500, color: "#f1d801"]
                ]
            }
            tileAttribute("device.power_details", key: "SECONDARY_CONTROL") {
                attributeState("power_details", label:'${currentValue}', defaultState: true)
            }
        }        
        
        valueTile("YearValue", "device.YearValue", width: 2, height: 2, inactiveLabel: false) {
			state "YearValue", label:'${currentValue} kWh', unit:""
		}
        valueTile("TotalValue", "device.TotalValue", width: 2, height: 2, inactiveLabel: false) {
			state "TotalValue", label:'${currentValue} kWh', unit:""
		}

		standardTile("HouseUsage", "HouseUsage", width: 4, height: 1) {
			state "default", label: "House Usage"
		}
		valueTile("HousePower", "device.house_power", width: 2, height: 1, inactiveLabel: false) {
			state "HousePower", label:'${currentValue} W', unit:""
		}
        
        standardTile("GridPower", "GridPower", width: 4, height: 1) {
			state "default", label: "Grid Power"
		}
        valueTile("Grid", "device.grid", width: 2, height: 1, inactiveLabel: false) {
			state "Grid", label:'${currentValue} W', unit:""
		}
        
		standardTile("poll", "device.poll", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false, decoration: "flat") {
			state "poll", label: "", action: "polling.poll", icon: "st.secondary.refresh", backgroundColor: "#FFFFFF"
		}
        
        main(["solar"])
		details(["solar", "HouseUsage", "HousePower", "GridPower", "Grid", "YearValue", "TotalValue", "poll"])
	}
}

def initialize() {
	log.info "Fronius Inverter ${textVersion()}"
    sendEvent(name: "solar_power", value: 0	)
    sendEvent(name: "YearValue", value: 0 )
    sendEvent(name: "energy", value: 0 )
    sendEvent(name: "TotalValue", value: 0 )
    sendEvent(name: "house_power", value: 0 )
    sendEvent(name: "grid", value: 0 )
	poll()
}

// parse events into attributes
def parse(String description) {	
    def msg = parseLanMessage(description)

    // log.info "Message: $msg"
    def result = msg.json
    
    log.info "JSON: $result"
    if (result.Head.RequestArguments.DeviceClass == "Meter") {
		// Parse Data From Smart Meter
    } else {
    	// Parse Data From Inverter
        def P_Grid = result.Body.Data.Site.P_Grid;
        
        def P_Load = (0 - result.Body.Data.Site.P_Load);
        
        def P_PV = 0
        if (result.Body.Data.Site.P_PV != null) {
            P_PV = result.Body.Data.Site.P_PV
            }

        def E_Year = result.Body.Data.Site.E_Year
        def E_Day = result.Body.Data.Site.E_Day
        def E_Total = result.Body.Data.Site.E_Total

        [name: "solar_power", value: Math.round(P_PV), unit: "W"]
        [name: "energy", value: (E_Day/1000), unit: "kWh"]
        [name: "house_power", value: Math.round(P_Load), unit: "W"]
        [name: "grid", value: Math.round(P_Grid), unit: "W"]

        sendEvent(name: "solar_power", value: P_PV )
        sendEvent(name: "energy", value: E_Day )
        sendEvent(name: "house_power", value: Math.round(P_Load) )
        sendEvent(name: "grid", value: Math.round(P_Grid) )
        sendEvent(name: "YearValue", value: Math.round((E_Year/1000) * 100.0) /100.0 )
        sendEvent(name: "TotalValue", value: Math.round((E_Total/1000) * 100.0) /100.0 )
        sendEvent(name: 'power_details', value: "Energy: " +Math.round((E_Day/1000) * 100.0) /100.0+" kWh", displayed: false)
    }
}

// handle commands
def poll() {
	def powerFlow = "/solar_api/v1/GetPowerFlowRealtimeData.fcgi"
    def meterRealtime = "/solar_api/v1/GetMeterRealtimeData.cgi?Scope=System"
    def inverter = callInvertor(powerFlow)
    def meter = callInvertor(meterRealtime)

	return [inverter, meter]
}

def callInvertor(path) {
	try
    {
	def hosthex = convertIPtoHex(destIp)
    def porthex = convertPortToHex(destPort)
    device.deviceNetworkId = "$hosthex:$porthex" 

    def hubAction = new physicalgraph.device.HubAction(
   	 		'method': 'GET',
    		'path': path,
        	'headers': [ HOST: "$destIp:$destPort" ]
		) 
    
    hubAction
    }
    catch (Exception e) {
        log.debug "Hit Exception $e on $hubAction"
    }
}

private def textVersion() {
    def text = "Version 1.0"
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}