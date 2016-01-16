local component = require("component")
local computer = require("computer")
local term = require("term")
local event = require("event")
local fs = require("filesystem")
monitor_textScale = 0.5

Style = {
	CDefault = 0xFFFFFF,
	BGDefault = 0x0000FF,

	CTitle = 0x000000,
	BGTitle = 0x00FFFF,

	CWarning = 0xFFFFFF,
	BGWarning = 0xFF0000,

	CSuccess = 0xFFFFFF,
	BGSuccess = 0x32CD32,

	CDisabled = 0x808080,
	BGDisabled = 0x000080
}

if not term.isAvailable() then
  computer.beep()
  return
end

----------- Monitor support

-- need to memorize colors so we can see debug stack dump
local gpu_frontColor = 0xFFFFFF
local gpu_backgroundColor = 0x000000
function SetMonitorColorFrontBack(frontColor, backgroundColor)
	gpu_frontColor = frontColor
	gpu_backgroundColor = backgroundColor
end

function Write(text)
	if term.isAvailable() then
		local w, h = component.gpu.getResolution()
		if w then
			local xt, yt = term.getCursor()
			component.gpu.setBackground(gpu_backgroundColor)
			component.gpu.setForeground(gpu_frontColor)
			component.gpu.set(xt, yt, text)
			SetCursorPos(xt + #text, yt)
			component.gpu.setBackground(0x000000)
		end
	end
end

function SetCursorPos(x, y)
  term.setCursor(x, y)
end

function SetColorDefault()
	SetMonitorColorFrontBack(Style.CDefault, Style.BGDefault)
end

function SetColorTitle()
	SetMonitorColorFrontBack(Style.CTitle, Style.BGTitle)
end

function SetColorWarning()
	SetMonitorColorFrontBack(Style.CWarning, Style.BGWarning)
end

function SetColorSuccess()
	SetMonitorColorFrontBack(Style.CSuccess, Style.BGSuccess)
end

function SetColorDisabled()
	SetMonitorColorFrontBack(Style.CDisabled, Style.BGDisabled)
end

function Clear()
	clearWarningTick = -1
	SetColorDefault()
	component.gpu.setBackground(gpu_backgroundColor)
	component.gpu.setForeground(gpu_frontColor)
	term.clear()
	component.gpu.setBackground(0x000000)
	SetCursorPos(1, 1)
end

function ClearLine()
	SetColorDefault()
	term.clearLine()
	SetCursorPos(1, 1)
end

function WriteLn(text)
	Write(text)
	local x, y = term.getCursor()
	local width, height = component.gpu.getResolution()
	if y > height - 1 then
		y = 1
	end
	SetCursorPos(1, y + 1)
end

function WriteCentered(y, text)
	if term.isAvailable() then
		local sizeX, sizeY = component.gpu.getResolution()
		if sizeX then
			component.gpu.setBackground(gpu_backgroundColor)
			component.gpu.setForeground(gpu_frontColor)
			component.gpu.set((sizeX - text:len()) / 2, y, text)
			component.gpu.setBackground(0x000000)
		end
	end
	local xt, yt = term.getCursor()
	SetCursorPos(1, yt + 1)
end

function ShowTitle(text)
	Clear()
	SetColorTitle()
	WriteCentered(1, text)
	SetColorDefault()
end

function ShowMenu(text)
	Write(text)
    local sizeX, sizeY = component.gpu.getResolution()
	local xt, yt = term.getCursor()
	for i = xt, sizeX do
		Write(" ")
	end
	SetCursorPos(1, yt + 1)
end

local clearWarningTick = -1
function ShowWarning(text)
    local sizeX, sizeY = component.gpu.getResolution()
	SetColorWarning()
	SetCursorPos((sizeX - text:len() - 2) / 2, sizeY)
	Write(" " .. text .. " ")
	SetColorDefault()
	clearWarningTick = 5
end
function ClearWarning()
	if clearWarningTick > 0 then
		clearWarningTick = clearWarningTick - 1
	elseif clearWarningTick == 0 then
		SetColorDefault()
		local sizeX, sizeY = component.gpu.getResolution()
		SetCursorPos(1, sizeY)
		ClearLine()
	end
end

----------- Formatting & popups

function FormatFloat(value, nbchar)
	local str = "?"
	if value ~= nil then
		str = string.format("%g", value)
	end
	if nbchar ~= nil then
		str = string.sub("               " .. str, -nbchar)
	end
	return str
end
function FormatInteger(value, nbchar)
	local str = "?"
	if value ~= nil then
		str = string.format("%d", value)
	end
	if nbchar ~= nil then
		str = string.sub("               " .. str, -nbchar)
	end
	return str
end

function boolToYesNo(bool)
	if bool then
		return "YES"
	else
		return "no"
	end
end

function readInputNumber(currentValue)
	local inputAbort = false
	local input = string.format(currentValue)
	if input == "0" then
		input = ""
	end
	local x, y = term.getCursor()
	repeat
		ClearWarning()
		SetColorDefault()
		SetCursorPos(x, y)
		Write(input .. "            ")
		input = string.sub(input, -9)
		
		local params = { event.pull() }
		local eventName = params[1]
		local address = params[2]
		if address == nil then address = "none" end
		if eventName == "key_down" then
			local char = params[3]
			local keycode = params[4]
			if char >= 49 and char <= 57 then -- 1 to 9
				input = input .. string.format(char - 48)
			elseif char == 48 then -- 0
				input = input .. "0"
			elseif char == 8 then -- Backspace
				input = string.sub(input, 1, string.len(input) - 1)
			elseif char == 0 and keycode == 211 then -- Delete
				input = ""
			elseif char == 13 then -- Enter
				inputAbort = true
			else
				ShowWarning("Key " .. char .. " " .. keycode .. " is invalid")
			end
		elseif eventName == "key_up" then
			-- drop it
		elseif eventName == "terminate" then
			inputAbort = true
		elseif not common_event(eventName, params[3]) then
			ShowWarning("Event '" .. eventName .. "', " .. address .. " is unsupported")
		end
	until inputAbort
	SetCursorPos(1, y + 1)
	if input == "" then
		return currentValue
	else
		return tonumber(input)
	end
end

function readInputText(currentValue)
	local inputAbort = false
	local input = string.format(currentValue)
	local x, y = term.getCursor()
	repeat
		ClearWarning()
		SetColorDefault()
		SetCursorPos(x, y)
		Write(input .. "                              ")
		input = string.sub(input, -30)
		
		local params = { event.pull() }
		local eventName = params[1]
		local address = params[2]
		if address == nil then address = "none" end
		if eventName == "key" then
			local char = params[3]
			local keycode = params[4]
			if char >= ' ' and char <= '~' then -- 1 to 9
				input = input .. char
			elseif char ~= 0 then
				ShowWarning("Char #" .. string.byte(char) .. " is invalid")
			elseif char == 8 then -- Backspace
				input = string.sub(input, 1, string.len(input) - 1)
			elseif char == 0 and keycode == 211 then -- Delete
				input = ""
			elseif char == 13 then -- Enter
				inputAbort = true
			else
				-- ShowWarning("Key " .. char .. " " .. keycode .. " is invalid")
			end
		elseif eventName == "key_up" then
			-- drop it
		elseif eventName == "terminate" then
			inputAbort = true
		elseif not common_event(eventName, params[3]) then
			ShowWarning("Event '" .. eventName .. "', " .. address .. " is unsupported")
		end
	until inputAbort
	SetCursorPos(1, y + 1)
	if input == "" then
		return currentValue
	else
		return input
	end
end

function readConfirmation()
	ShowWarning("Are you sure? (y/n)")
	repeat
		local params = { event.pull() }
		local eventName = params[1]
		local address = params[3]
		if address == nil then address = "none" end
		if eventName == "key_down" then
			local char = params[3]
			if char == 89 or char == 121 then -- Y
				return true
			else
				return false
			end
		elseif eventName == "key_up" then
			-- drop it
		elseif eventName == "terminate" then
			return false
		elseif not common_event(eventName, params[3]) then
			ShowWarning("Event '" .. eventName .. "', " .. address .. " is unsupported")
		end
	until false
end

----------- commons: menu, event handlers, etc.

function common_event(eventName, param)
	if eventName == "redstone" then
--		redstone_event(param)
	elseif eventName == "timer" then
	elseif eventName == "reactorPulse" then
		reactor_pulse(param)
--	elseif eventName == "reactorDeactivation" then
--		ShowWarning("Reactor deactivated")
--	elseif eventName == "reactorActivation" then
--		ShowWarning("Reactor activated")
	else
		return false
	end
	return true
end

function menu_common()
	SetCursorPos(1, 18)
	SetColorTitle()
	ShowMenu("0 Connections, 1 Reactor, X Exit")
end

----------- Configuration

function data_save()
	local file = fs.open("shipdata.txt", "w")
	if file ~= nil then
		file.writeLine(textutils.serialize(data))
		file.close()
	else
		ShowWarning("No file system")
	end
end

function data_read()
	if fs.exists("shipdata.txt") then
		local file = fs.open("shipdata.txt", "r")
		data = textutils.unserialize(file.readAll())
		file.close()
	else
		data = { }
	end
	if data.reactor_mode == nil then data.reactor_mode = 0; end
	if data.reactor_rate == nil then data.reactor_rate = 100; end
	if data.reactor_targetStability == nil then data.reactor_targetStability = 50; end
	if data.reactor_laserAmount == nil then data.reactor_laserAmount = 10000; end
end

function data_setName()
	ShowTitle("<==== Set name ====>")
	
	SetCursorPos(1, 2)
	Write("Enter ship name: ")
	label = readInputText(label)
	-- FIXME os.setComputerLabel(label)
	-- FIXME os.reboot()
end

----------- Reactor support

reactor_output = 0

function reactor_boot()
	if reactor ~= nil then
		WriteLn("Booting Reactor...")
		local isActive, strMode, releaseRate = reactor.active()
		if strMode == "OFF" then
			data.reactor_mode = 0
		elseif strMode == "MANUAL" then
			data.reactor_mode = 1
		elseif strMode == "ABOVE" then
			data.reactor_mode = 2
		elseif strMode == "RATE" then
			data.reactor_mode = 3
		else
			data.reactor_mode = 0
		end
	end
end

function reactor_key(char, keycode)
	if char == 83 or char == 115 then -- S
		reactor_start()
		return true
	elseif char == 80 or char == 112 then -- P
		reactor_stop()
		return true
	elseif char == 76 or char == 108 then -- L
		reactor_laser()
		return true
	elseif char == 79 or char == 111 then -- O
		data.reactor_mode = (data.reactor_mode + 1) % 4
		reactor_setMode()
		data_save()
		return true
	elseif char == 71 or char == 103 then -- G
		data.reactor_rate = data.reactor_rate / 10
		reactor_setMode()
		data_save()
		return true
	elseif char == 84 or char == 116 then -- T
		data.reactor_rate = data.reactor_rate * 10
		reactor_setMode()
		data_save()
		return true
	elseif char == 74 or char == 106 then -- J
		data.reactor_laserAmount = data.reactor_laserAmount / 10
		reactor_setLaser()
		data_save()
		return true
	elseif char == 85 or char == 117 then -- U
		data.reactor_laserAmount = data.reactor_laserAmount * 10
		reactor_setLaser()
		data_save()
		return true
	elseif char == 45 then -- -
		data.reactor_targetStability = data.reactor_targetStability - 1
		reactor_setTargetStability()
		data_save()
		return true
	elseif char == 43 then -- +
		data.reactor_targetStability = data.reactor_targetStability + 1
		reactor_setTargetStability()
		data_save()
		return true
	elseif char == 67 or char == 99 then -- C
		reactor_config()
		data_save()
		return true
	end
	return false
end

function reactor_page()
	ShowTitle(label .. " - Reactor status")

	SetCursorPos(1, 2)
	if reactor == nil then
		SetColorDisabled()
		Write("Reactor not detected")
	else
		SetColorDefault()
		Write("Reactor stability")
		instabilities = { reactor.instability() }
		average = 0
		for key,instability in pairs(instabilities) do
			SetCursorPos(12, 2 + key)
			stability = math.floor((100.0 - instability) * 10) / 10
			if stability >= data.reactor_targetStability then
				SetColorSuccess()
			else
				SetColorWarning()
			end
			Write(FormatFloat(stability, 5) .. " %")
			average = average + instability
		end
		average = average / #instabilities

		SetColorDefault()
		local energy = { reactor.energy() }
		SetCursorPos(1, 7)
		Write("Energy   : ")
		if energy[2] ~= nil then
			Write(FormatInteger(energy[1], 10) .. " / " .. energy[2] .. " RF +" .. FormatInteger(reactor_output, 5) .. " RF/t")
		else
			Write("???")
		end
		SetCursorPos(1, 8)
		Write("Outputing: ")
		if energy[3] ~= nil then
			Write(energy[3] .. " RF/t")
		end

		SetColorDefault()
		SetCursorPos(1, 9)
		Write("Activated: ")
		isActive = reactor.active()
		if isActive then SetColorSuccess() else SetColorDefault() end
		Write(boolToYesNo(isActive))
	end

	if #reactorlasers == 0 then
		SetColorDisabled()
		SetCursorPos(30, 2)
		Write("Lasers not detected")
	else
		SetColorDefault()
		SetCursorPos(30, 2)
		Write("Lasers")

		for key,reactorlaser in pairs(reactorlasers) do
			local side = reactorlaser.side()
			if side ~= nil then
				side = side % 4
				SetColorDefault()
				SetCursorPos(4, 3 + side)
				Write("Side " .. side .. ":")
				SetCursorPos(30, 3 + side)
				local energy = reactorlaser.energy()
				if not reactorlaser.hasReactor() then
					SetColorDisabled()
				elseif energy > 3 * data.reactor_laserAmount then
					SetColorSuccess()
				else
					SetColorWarning()
				end
				Write(FormatInteger(reactorlaser.energy(), 6))
			end
		end
	end

	SetColorDefault()
	SetCursorPos(1, 10)
	Write("  -----------------------------------------------")
	SetCursorPos(1, 11)
	Write("Output mode     : ")
	if data.reactor_mode == 0 then
		SetColorDisabled()
		Write("hold")
	elseif data.reactor_mode == 1 then
		Write("manual/unlimited")
	elseif data.reactor_mode == 2 then
		Write("surplus above " .. data.reactor_rate .. " RF")
	else
		Write("rated at " .. data.reactor_rate .. " RF")
	end
	SetColorDefault()
	SetCursorPos( 1, 12)
	Write("Target stability: " .. data.reactor_targetStability .. "%")
	SetCursorPos(30, 12)
	Write("Laser amount: " .. data.reactor_laserAmount)
	
	SetColorTitle()
	SetCursorPos(1, 14)
	ShowMenu("S - Start reactor, P - Stop reactor, L - Use lasers")
	SetCursorPos(1, 15)
	ShowMenu("O - Output mode, C - Configuration")
	SetCursorPos(1, 16)
	ShowMenu("+/- - Target stability, U/J - Laser amount")
	SetCursorPos(1, 17)
	ShowMenu("G/T - Output rate/threshold")
end

function reactor_setMode()
	if data.reactor_rate < 1 then
		data.reactor_rate = 1
	elseif data.reactor_rate > 100000 then
		data.reactor_rate = 100000
	end
	if reactor ~= nil then
		if data.reactor_mode == 0 then
			reactor.release(false)
		elseif data.reactor_mode == 1 then
			reactor.release(true)
		elseif data.reactor_mode == 2 then
			reactor.releaseAbove(data.reactor_rate)
		else
			reactor.releaseRate(data.reactor_rate)
		end
	end
end

function reactor_setLaser()
	if data.reactor_laserAmount < 1 then
		data.reactor_laserAmount = 1
	elseif data.reactor_laserAmount > 100000 then
		data.reactor_laserAmount = 100000
	end
end

function reactor_setTargetStability()
	if data.reactor_targetStability < 1 then
		data.reactor_targetStability = 1
	elseif data.reactor_targetStability > 100 then
		data.reactor_targetStability = 100
	end
end

function reactor_start()
	if reactor ~= nil then
		reactor_setMode()
		reactor.active(true)
	end
end

function reactor_stop()
	if reactor ~= nil then
		reactor.active(false)
	end
end

function reactor_laser(side)
	for key,reactorlaser in pairs(reactorlasers) do
		if (side == nil) or (reactorlaser.side() == side) then
			reactorlaser.stabilize(data.reactor_laserAmount)
		end
	end
end

function reactor_pulse(output)
	reactor_output = output
	if reactor == nil then
		-- FIXME os.reboot()
	end
	local instabilities = { reactor.instability() }
	for key,instability in pairs(instabilities) do
		local stability = math.floor((100.0 - instability) * 10) / 10
		if stability < data.reactor_targetStability then
			reactor_laser(key - 1)
		end
	end
end

function reactor_config()
	ShowTitle(label .. " - Reactor configuration")
	
	SetCursorPos(1, 2)
	if reactor == nil then
		SetColorDisabled()
		Write("Reactor not detected")
	else
		SetColorDefault()
		SetCursorPos(1, 4)
		Write("Reactor output rate (" .. data.reactor_rate .. " RF): ")
		data.reactor_rate = readInputNumber(data.reactor_rate)
		reactor_setMode()
		SetCursorPos(1, 5)
		Write("Reactor output rate set")
		
		SetCursorPos(1, 7)
		Write("Laser energy level (" .. data.reactor_laserAmount .. "): ")
		data.reactor_laserAmount = readInputNumber(data.reactor_laserAmount)
		reactor_setLaser()
		SetCursorPos(1, 8)
		Write("Laser energy level set")
		
		SetCursorPos(1, 10)
		Write("Reactor target stability (" .. data.reactor_targetStability .. "%): ")
		data.reactor_targetStability = readInputNumber(data.reactor_targetStability)
		reactor_setTargetStability()
		SetCursorPos(1, 11)
		Write("Reactor target stability set")
	end
end

----------- Boot sequence 
label = computer.address()
if not label then
	label = "" .. computer.address()
end

-- read configuration
data_read()

-- initial scanning
ShowTitle(label .. " - Connecting...")
WriteLn("")

reactor = nil
reactorlasers = {}
for address, componentType in component.list() do
	os.sleep(0)
	if componentType == "warpdriveEnanReactorCore" then
		WriteLn("Wrapping " .. componentType)
		reactor =  component.proxy(address)
	elseif componentType == "warpdriveEnanReactorLaser" then
		WriteLn("Wrapping " .. componentType)
		table.insert(reactorlasers, component.proxy(address))
	end
end
-- os.sleep(1)

if not computer.address() and reactor ~= nil then
	data_setName()
end

-- peripherals status
function connections_page()
	ShowTitle(label .. " - Connections")

	WriteLn("")
	if reactor == nil then
		SetColorDisabled()
		WriteLn("No Enantiomorphic reactor detected")
	else
		SetColorSuccess()
		WriteLn("Enantiomorphic reactor detected")
	end

	if #reactorlasers == 0 then
		SetColorDisabled()
		WriteLn("No reactor stabilisation laser detected")
	elseif #reactorlasers == 1 then
		SetColorSuccess()
		WriteLn("1 reactor stabilisation laser detected")
	else
		SetColorSuccess()
		WriteLn(#reactorlasers .. " reactor stabilisation lasers detected")
	end
end

-- peripheral boot up
Clear()
connections_page()
SetColorDefault()
WriteLn("")
os.sleep(0)
reactor_boot()
os.sleep(0)

-- main loop
abort = false
refresh = true
page = connections_page
keyHandler = nil
repeat
	ClearWarning()
	if refresh then
		Clear()
		page()
		menu_common()
		refresh = false
	end
	params = { event.pull() }
	eventName = params[1]
	address = params[2]
	if address == nil then address = "none" end
		WriteLn("...")
		WriteLn("Event '" .. eventName .. "', " .. address .. ", " .. params[3] .. " received")
		os.sleep(0.2)
	if eventName == "key_down" then
		char = params[3]
		keycode = params[4]
		if char == 88 or char == 120 then -- x for eXit
			abort = true
		elseif char == 48 then -- 0
			page = connections_page
			keyHandler = nil
			refresh = true
		elseif char == 49 then -- 1
			page = reactor_page
			keyHandler = reactor_key
			refresh = true
		elseif keyHandler ~= nil and keyHandler(char, keycode) then
			refresh = true
			os.sleep(0)
		elseif char == 0 then -- control chars
			refresh = false
			os.sleep(0)
		else
			ShowWarning("Key " .. char .. " " .. keycode .. " is invalid")
			os.sleep(0.2)
		end
		-- func(unpack(params))
		-- abort, refresh = false, false
	elseif eventName == "char" then
		-- drop it
	elseif eventName == "key_up" then
		-- drop it
	elseif eventName == "reactorPulse" then
		ShowWarning("reactorPulse")
		reactor_pulse(params[3])
		refresh = (page == reactor_page)
	elseif eventName == "terminate" then
		abort = true
	elseif not common_event(eventName, params[3]) then
		ShowWarning("Event '" .. eventName .. "', " .. address .. " is unsupported")
		refresh = true
		os.sleep(0.2)
	end
until abort

-- clear screens on exit
SetMonitorColorFrontBack(0xFFFFFF, 0x000000)
term.clear()
SetCursorPos(1, 1)
Write("")
