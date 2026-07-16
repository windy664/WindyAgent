---
title: CMI 对话框
tags: CMI, 命令, dialogs
source: https://www.zrips.net/cmi/commands/dialogs/
---
Dialog commands can utilize **specialized command** mechanics, and you can read more about them **[[CMI 专用命令|here]]**.

![](https://www.zrips.net/wp-content/uploads/2025/08/dialogs.png)

The main thing to note is that the main Label is always at the top, which is optional, then follows visual fields, input fields, and custom buttons in this specific order. This is due to Minecraft’s handling of Dialog windows. Might change in the future.

Example of dialog windows. Keep in mind that you can have multiple files and multiple Dialogs inside each file for better organization.

```
welcome:
  Enabled: true
  Label: '{#white}Welcome {#gray}%cmi_user_display_name% {#white}to {#edward}%cmi_user_world_formatted%'
  Description:
    Width: 300
    Lines: 
    - '{#green}Best minecraft server on earth!'
    - ''
    - '{#white}We are glad to have you here %cmi_user_display_name%{#white}!'
    - 'If you have any questions, feel free to ask our staff'
  VisualFields:
  - Width: 300
    Lines: 
    - '{#brown}Extra info line!'
  Buttons:
    Columns: 3 
    List:
    - Label: '{#red}Read The Rules' 
      Width: 100
      OpenDialog: license
      Tooltip: 
      - '{#red}Read it before starting to play!'
    - Label: '{#green>}Read a joke{#darkgreen<}'
      Width: 100
      OpenDialog: 'randomtext'
    - Label: '{#edward>}Start Playing{#darkgreen<}'
      Width: 100
      Commands: 
      - closeinv!
    - Label: '{#blue}Select a thing'
      Width: 100
      OpenDialog: inputexample
    - Label: ''
      Width: 1
    - Label: '{#yellow}Visual examples'
      Width: 100
      OpenDialog: visualexample
    - Label: '{#yellow}Visit Our Website %cmi_user_display_name%'
      Width: 304
      Url: www.zrips.net
  Close:
    Label: '{#pink>}Go Away!{#edward<}'
    Tooltip: 
    - '{#pink>}Lets go %cmi_user_display_name%!{#edward<}'
    Commands: 
    - 'cmi feed [playerName]'
    Width: 150
visualexample:
  Enabled: true
  Label: '{#white}Visual examples'
  Description:
    Width: 300
    Lines: 
    - '{#green}Descrption at the top!'
  VisualFields:
  - Width: 300
    Lines: 
    - '{#brown}Extra info line!'
  - Item: Stone;n{{#red}Custom name};e{sharpness:2}
    Text:
      Width: 150
      Lines: 
      - '{#green}Item text with some auto wrapping and'
      - ''
      - '{#white}Multiline'
    Width: 16
    Height: 16
    ShowTooltip: true
    ShowDecorations: false
  - Item: potato
    Text:
      Width: 150
      Lines: 
      - '{#green}No tooltip on item'
    Width: 16
    Height: 16
    ShowTooltip: false
  - Item: '[ItemInHand]'
    Text:
      Lines: 
      - '{#green}%cmi_iteminhand_displayname% {#white}is what you are holding in your hand!'
  Close:
    Label: '{#edward}Go Back!'
    OpenDialog: welcome
    Width: 250
inputexample:
  Enabled: true
  Label: '{#white}Example of inputs'
  Inputs:
  - Label: '{#brown}Pick one'
    Width: 150
    Options:
    - Label: Option A
      OpenDialog: 'randomtext'
    - Label: '{#red}Option B'
      Default: true
      Commands:
      - 'cmi heal [playerName]'
    - Label: Option C
  - Label: 'Select it and get healed on save!'
    OnTrue: 
      Commands:
      - cmi heal [playerName]
    OnFalse: 
      Commands:
      - msg! [playerName] No free heal for you!
  Buttons:
    Columns: 1 
    List:
    - Label: '{#yellow}Save'
      Width: 150
      Save: true
  Close:
    Label: '{#pink}Back'
    OpenDialog: welcome
license:
  Enabled: true
  Label: '{#white}User Agreement'
  VisualFields:
  - Width: 350
    Lines: 
    - 'By clicking “Agree with terms” or continuing to play on this server, you solemnly swear that:'
    - ''
    - ''
    - ''
    - '{#white}1. You will not grief, steal, or blow up someone’s house unless they literally begged you to. (And even then, record it for legal reasons.)'
    - '{#nobel}2. You will not hack, fly, X-ray, or teleport through walls like a haunted AI. We like our ghosts dead and our walls solid.'
    - '{#white}3. You agree that admins are always right, especially when they’re wrong. Disputes may be settled in a pit of lava.'
    - '{#nobel}4. You will not spam the chat, unless it’s with memes approved by the Ministry of Silly Blocks™.'
    - '{#white}5. You will build things, not just holes in the ground shaped like something inappropriate.'
    - '{#nobel}6. You will not summon the Wither inside spawn. Seriously. That’s a one-way ticket to banville.'
    - "{#white}7. You accept that lag may happen, creepers may explode, and your pet dog may randomly vanish. It’s Minecraft, not therapy."
    - "{#nobel}8. You promise not to ask for op, creative mode, free diamonds, or the admin’s credit card number."
    - '{#white}9. You accept that this is a game, not a substitute for real-world dominance, revenge, or romance.'
    - "{#nobel}10. You acknowledge that your building may be roasted — gently and with love — if it's a dirt cube."
    - ''
    - ''
    - '{#white}By accepting, you agree to play nice, have fun, and not become the reason we disable TNT again.'
    - ''
    - '{#yellow}Click to enter blocky paradise.'
    - 'Missclick to be gently kicked like a misbehaving parrot.'
  Inputs:
  - Type: Boolean
    Label: 'Agree with terms'
    OnTrue: 
      Commands:
      - asConsole! cmi heal [playerName]
      - closeinv!
    OnFalse:
      Commands:
      - asConsole! cmi kick [playerName] You disagreed! 
  Close:
    Label: 'Update!'
    Save: true
    Width: 150
```

```
  Close:
    Label: 'Update!'
    Save: true
    Width: 150
```

Random Text

Basic Shop

Random Text

```
randomtext:
  Enabled: true
  Description:
    Randomize: true
    Lines: 
    - "{#yellow}Why did the creeper break up with the skeleton?\n\n{#green}Because he felt blown away!"
    - "{#yellow}Why don't Endermen like jokes?\n\n{#green}They can't take things lightly!"
    - '{#yellow}Why did Steve get lost?\n\n{#green}He didn’t follow the chunky breadcrumbs!'
    - '{#yellow}How does a Minecraft player avoid sunburn?\n\n{#green}Stay in the shade biome!'
    - '{#yellow}Why was the Minecraft player a great musician?\n\n{#green}He had perfect block timing!'
    - '{#yellow}Why did the chicken cross the Nether?\n\n{#green}To get to the other biome!'
    - '{#yellow}How do you start a party in Minecraft?\n\n{#green}You block the creepers!'
    - '{#yellow}Why was the villager so good at trading?\n\n{#green}Because he had emerald standards!'
    - '{#yellow}What do you call a Minecraft celebration?\n\n{#green}A block party!'
    - '{#yellow}What’s a skeleton’s least favorite room?\n\n{#green}The living room!'
  Close:
    Label: 'Back'
    OpenDialog: 'welcome'
    Width: 150
```

Basic Shop

```
buyItemExample:
  Enabled: true
  Label: '{#white}Shop'
  Description:
    Width: 300
    Lines: 
    - '<T>{#green}General store</T><H>Buy stuff</H>'
  VisualFields:
  - Item: "[material]"
    Width: 16
    Height: 16
    ShowTooltip: false  
  - Width: 250
    Lines: 
    - '<T>{#green}Buy %cmi_material_realname_[material]% for {#yellow}%cmi_worth_buy_[material]% {#green}each</T><H>Hover text</H>'
    - '{#green}You have %cmi_material_realname_[material]% X {#yellow}%cmi_user_itemcount_[material]% {#green}in your inventory'
    - '{#green}Your balance {#yellow}%cmi_user_balance_formated%'
  - Width: 300
    Placeholder: '%cmi_user_balance%<%cmi_worthc_buy_[material]%'
    Lines:
    - '{#red}No money!'
  - Width: 300
    Placeholder: '%cmi_worthc_buy_[material]%<=0' 
    Lines:
    - '{#yellow}Not for sale!'
  Buttons:
    Columns: 2
    List:
    - Label: 'Buy %cmi_material_realname_[material]% {#gray}x1' 
      Placeholder:
      - '%cmi_worthc_buy_[material]%>0'
      - '%cmi_user_balance%>=%cmi_worthc_buy_[material]%'
      Width: 200
      Tooltip:
      - '{#white}Price:{#yellow} %cmi_worth_buy_[material]%'
      - '{#gray}Click to buy %cmi_material_realname_[material]%'
      Commands:
      - moneycost:%cmi_worthc_buy_[material]%! cmi give [playerName] [material] 1
      - asConsole! cmi dialogs buyItemExample [playerName] material:[material]
    - Label: 'Sell %cmi_material_realname_[material]% {#gray}x1' 
      Placeholder:
      - '%cmi_worthc_sell_[material]%>0'
      - '%cmi_user_itemcount_[material]%>=1'
      Width: 200
      Tooltip:
      - '{#white}Price:{#yellow} %cmi_worth_sell_[material]%'
      - '{#gray}Click to sell %cmi_material_realname_[material]%'
      Commands:
      - exactitem:[material]! asConsole! cmi money give [playerName] %cmi_worthc_sell_[material]%
      - asConsole! cmi dialogs buyItemExample [playerName] material:[material]
    - Label: 'Buy %cmi_material_realname_[material]% {#gray}x10' 
      Placeholder:
      - '%cmi_worthc_buy_[material]%>0'
      - '%cmi_user_balance%>=%cmi_worthc_buy_[material]:10%'
      Width: 200
      Tooltip:
      - '{#white}Price:{#yellow} %cmi_worth_buy_[material]:10%'
      - '{#gray}Click to buy %cmi_material_realname_[material]%'
      Commands:
      - moneycost:%cmi_worthc_buy_[material]:10%! cmi give [playerName] [material] 10
      - asConsole! cmi dialogs buyItemExample [playerName] material:[material]
    - Label: 'Sell %cmi_material_realname_[material]% {#gray}x10' 
      Placeholder:
      - '%cmi_worthc_sell_[material]%>0'
      - '%cmi_user_itemcount_[material]%>=10'
      Width: 200
      Tooltip:
      - '{#white}Price:{#yellow} %cmi_worth_sell_[material]:10%'
      - '{#gray}Click to sell %cmi_material_realname_[material]%'
      Commands:
      - exactitem:[material]:10! asConsole! cmi money give [playerName] %cmi_worthc_sell_[material]:10%
      - asConsole! cmi dialogs buyItemExample [playerName] material:[material]
    - Label: 'Buy %cmi_material_realname_[material]% {#gray}x64' 
      Placeholder:
      - '%cmi_worthc_buy_[material]%>0'
      - '%cmi_user_balance%>=%cmi_worthc_buy_[material]:64%'
      Width: 200
      Tooltip:
      - '{#white}Price:{#yellow} %cmi_worth_buy_[material]:64%'
      - '{#gray}Click to buy %cmi_material_realname_[material]%'
      Commands:
      - moneycost:%cmi_worthc_buy_[material]:64%! cmi give [playerName] [material] 64
      - asConsole! cmi dialogs buyItemExample [playerName] material:[material]
    - Label: 'Sell %cmi_material_realname_[material]% {#gray}x64' 
      Placeholder:
      - '%cmi_worthc_sell_[material]%>0'
      - '%cmi_user_itemcount_[material]%>=64'
      Width: 200
      Tooltip:
      - '{#white}Price:{#yellow} %cmi_worth_sell_[material]:64%'
      - '{#gray}Click to sell %cmi_material_realname_[material]%'
      Commands:
      - exactitem:[material]:64! asConsole! cmi money give [playerName] %cmi_worthc_sell_[material]:64%
      - asConsole! cmi dialogs buyItemExample [playerName] material:[material]
```

## Base

```
textfieldtest:
  Enabled: true
  Label: '{#white}Title text'
  Close:
    Label: '{#pink}Back'
    OpenDialog: welcome
```

Each dialog setup starts with its name, which will be used in a command or when opening this dialog from another dialog.  
**Enabled:** Will define if this dialog should be enabled. When it’s set to false, then only players with access to **cmi.command.dialogs.disabled** will be able to see it.  
**Label:** Will define the top text of the Dialog window. This can contain placeholders, which will be translated based on the player who is looking at this window.  
**Permission**: permission node required to use this dialog window. Optional.  
**Placeholder:** placeholder node which can define requirement to see dialog/button/input/visual fields  
**Close:** an optional field to create a close button, utilizes the same options as general buttons. If not provided, then there won’t be a close button, but you can always close the Dialog window by pressing the ESC key on your keyboard. Recommended to keep this one present. Keep in mind that the action from the Close button will be performed when you press ESC on your keyboard.

## Conditions

Dialogs itself, its buttons, input and visual fields can have conditions attached to them to indicate if you can use them or not. You can either request specific permission node or/and specific placeholder value.

```
  Placeholder: '%cmi_iteminhand_type%==stone'  Permission: 'any.permission.node'
```

#### Placeholder

```
 Placeholder: '%cmi_iteminhand_type%==stone'
```

```
  Placeholder:
  - '%cmi_worthc_buy_[material]%>0'
  - '%cmi_user_balance%>=%cmi_worthc_buy_[material]%'
```

Placeholder section should either contain placeholder which values returns one of the default values, for positive: **true, yes, on, enable, enabled, 1** and for negative will be any non matching value. Return value should be clean and without any color codes. Alternatively you can define custom value you want to check for which should be defined after **\==** variable, for example **‘****%cmi_user_weather%==sunny’** which would only allow use of dialogs when player is in a world with sunny type weather

Placeholder fields does accept custom variables passed over with a dialogs command

You can define multiple placeholders as requirements which will mean that player will need to have all of them to be able to see that field

Numerical values can be used, for example **‘%cmi_user_stats_kill_entity:Zombie%>=20’** will check if placeholder returned value is equal or higher than 20. Valid actions: **>= <= > <**

#### Permission

```
 Permission: 'any.permission.node'
```

Any permission node required for player to have to use dialog or its fields.  
If permission node starts with **!** then we will be checking if player doesn’t have this permission node, for example **‘!cmi.command.heal’** which only show fields to the players without this permission node

## VisualFields

```
  VisualFields:
  - Width: 300
    Lines: 
    - '{#brown}Extra info line!'
  - Item: Stone;n{{#red}Custom name};e{sharpness:2}
    Text:
      Width: 150
      Lines: 
      - '{#green}Item text with some auto wrapping and'
      - ''
      - '{#white}Multiline'
    Width: 16
    Height: 16
    ShowTooltip: true
    ShowDecorations: false
  - Item: potato
    Text:
      Width: 150
      Lines: 
      - '{#green}No tooltip on item'
    Width: 16
    Height: 16
    ShowTooltip: false
  - Item: '[ItemInHand]'
    Text:
      Lines: 
      - '{#green}%cmi_iteminhand_displayname% {#white}is what you are holding in your hand!'
```

#### Text

```
  - Width: 300
    Lines: 
    - '{#brown}Extra info line!'
```

    **Width**: defines the width of the text field. If the text can’t fit in, then it will be auto-wrapped to the following line.  
    **Permission**: permission node required to see this text. Optional.  
    **Placeholder:** placeholder node required to see this text. Optional.  
    **Randomize:** when enabled, we will pick one random line from the provided ones.  
    **Lines**: a list of lines to be shown for the player, can contain placeholders. Can use CText format to show text with hover over abilities and perform commands when clicked

#### item

```
  - Item: Stone;n{{#red}Custom name};e{sharpness:2}
    Text:
      Width: 150
      Lines: 
      - '{#green}Item text with some auto wrapping and'
      - ''
      - '{#white}Multiline'
    Width: 16
    Height: 16
    ShowTooltip: true
    ShowDecorations: false
  - Item: potato
    Text:
      Width: 150
      Lines: 
      - '{#green}No tooltip on item'
    Width: 16
    Height: 16
    ShowTooltip: false
  - Item: '[ItemInHand]'
    Text:
      Lines: 
      - '{#green}%cmi_iteminhand_displayname% {#white}is what you are holding in your hand!'
```

    **Item:** definition of item, accepts one liner items like Stone;n{{#red}Custom name};e{sharpness:2} or [ItemInHand], which will include the player’s item from his main hand automatically. Placeholders can be used here, which returns a properly formatted item description.  
    **Permission**: permission node required to see this item. Optional.  
    **Placeholder:** placeholder node required to see this item. Optional.  
    **Width/Height:** defines items field size, keep in mind that due to minecrafts limitation this only resizes field around items icon but doesn’t resize actual icon. So, while we have these options, they currently have no real use.  
    **ShowTooltip**: will show or hide the item’s tooltip information when you hover over it  
    **ShowDecorations**: will show or hide an item’s decorations in its tooltip, which includes things like the item’s durability bar or item amount.  
    **Text:** contains generic text field variables. Can use CText format to show text with hover over abilities and perform commands when clicked  
    **Width**: text field width.  
    **Lines**: text lines to be shown on the right side of the item. Can contain placeholders.

## Inputs

```
inputexample:
  Enabled: true
  Label: '{#white}Example of inputs'
  Inputs:
  - Label: '{#brown}Pick one'
    Width: 150
    Options:
    - Label: Option A
      OpenDialog: 'randomtext'
    - Label: '{#red}Option B'
      Default: true
      Commands:
      - 'cmi heal [playerName]'
    - Label: Option C
      Commands:
      - 'msg! [playerName] selected [value]'
  - Label: '{#brown}Select dialog to open'
    Width: 250
    OpenDialog: '[value]'
    Options:
    - Label: '{#white}None'
      Value: none
    - Label: '{#pink}Welcome'
      Value: welcome
    - Label: '{#green}License'
      Value: license
    - Label: '{#red}Visual Example'
      Value: visualexample
  - Label: 'Select it and get healed on save!'
    OnTrue: 
      Commands:
      - cmi heal [playerName]
      Value: accepted
    OnFalse: 
      Commands:
      - msg! [playerName] No free heal for you as you picked [value]!
      Value: deny!!!!
  - Label: 'Placeholder based'
    Default: '%cmi_maintenance_state%'
    OnTrue: 
      Commands:
      - cmi maintenance true
    OnFalse: 
      Commands:
      - cmi maintenance false
  - Label: 'Text over input'
    InitialText: 'Initial text to show'
    MaxLength: 256
    MaxLines: 5
    Height: 64
    Width: 350
    Commands:
    - 'msg! [playerName] &2[value]'
  - Type: Text
    Width: 500
    Commands:
    - 'msg! [playerName] &2[value]'
  - InitialText: '%cmi_user_nickname%'
    Width: 250
    MaxLines: 5
    Height: 64
    Commands:
    - 'cmi nick [value] [playerName]'
  - Label: 'Max players %server_max_players%'
    Width: 250
    Start: 0
    End: '%cmi_equationint_{server_max_players}*2%'
    Step: 1
    InitialValue: '%server_max_players%'
    Commands:
    - 'cmi maxplayers [value]'
  Buttons:
    Columns: 1 
    List:
    - Label: '{#yellow}Save'
      Width: 150
      Save: true
  Close:
    Label: '{#pink}Back'
    OpenDialog: welcome
```

In most cases, input type will be determined automatically, but this requires you to define at least one specific, unique field for that to happen. In case type isn’t being detected correctly, you can define it manually by using **Type** variable, valid options: **Singleton, Boolean, Text, Slider**. For example 

```
  - Type: Text
```

#### Singleton

```
  - Label: '{#brown}Pick one'
    Width: 150
    Options:
    - Label: Option A
      OpenDialog: 'randomtext'
    - Label: '{#red}Option B'
      Default: true
      Commands:
      - 'cmi heal [playerName]'
    - Label: Option C
      Commands:
      - 'msg! [playerName] selected [value]'
  - Label: '{#brown}Select dialog to open'
    Width: 250
    OpenDialog: '[value]'
    Options:
    - Label: '{#white}None'
      Value: none
    - Label: '{#pink}Welcome'
      Value: welcome
    - Label: '{#green}License'
      Value: license
    - Label: '{#red}Visual Example'
      Value: visualexample
```

    Label: optional text to be shown for each button selection  
    **Permission**: permission node required to see and use input. Optional.  
    **Placeholder:** placeholder node required to use this input. Optional.  
    Width: defines the width of the button  
    Options: a list of options the player can pick from        
        Label: options label, which will be shown when selecting. Can contain placeholders  
        **Value**: custom value of this singleton selection, which can then be used in commands or OpenDialog with [value] variable  
        Commands: defines commands to be performed when this option is selected and the save button is pressed. Optional field.  
        Default: if present and set to true, then it will be used as the default option shown at the start of selection. This is an entirely optional field, and if not provided, we will use the  first entry. This field and use placeholders which should return boolean like value: **1 0 true false** In case multiple options results in true then first one will be used, if placeholders state can’t be determined then we will be defaulting to the first option

#### Boolean

```
  - Label: 'Select it and get healed on save!'
    OnTrue: 
      Commands:
      - cmi heal [playerName]
      Value: accepted
    OnFalse: 
      Commands:
      - msg! [playerName] No free heal for you as you picked [value]!
      Value: deny!!!!
  - Label: 'Placeholder based'
    Default: '%cmi_maintenance_state%'
    OnTrue: 
      Commands:
      - cmi maintenance true
    OnFalse: 
      Commands:
      - cmi maintenance false
```

    Label: Text to be shown on the side of the tick box, this can be one or multiple lines. Optional field  
    **Permission**: permission node required to see and use input. Optional.  
    **Placeholder:** placeholder node required to use this input. Optional.  
    OnTrue:   
        Commands: list of commands to be performed when the check box is selected. Optional  
        Value: custom value of this singleton selection which can then be used in commands or OpenDialog with [value] variable  
    OnFalse:   
        Commands: list of commands to be performed when the check box is not selected. Optional

#### Text

```
  - Type: Text
    Width: 500
    Commands:
    - 'msg! [playerName] &2[value]'
  - InitialText: '%cmi_user_nickname%'
    Width: 250
    MaxLines: 5
    Height: 64
    Commands:
    - 'cmi nick [value] [playerName]'
```

    Label: text to be shown on top of the text field. Optional  
    **Permission**: permission node required to see and use input. Optional.  
    **Placeholder:** placeholder node required to use this input. Optional.  
    **Initialtext**: text to be shown inside text field itself as initial value. This can contain a placeholder value like %cmi_user_nickname% to insert player’s current nickname value.  
    **MaxLength**: maximum amount of characters the player can enter into this field  
    **MaxLines**: enables the option to wrap lines into multiple lines, allowing for a simpler visual representation in case the input text is wider than the input field itself.  
    **Height**: height of input field, can be used together with MaxLines to have an appropriately sized input field  
    **Width:** width of input field, if not provided, this might auto-adjust based on MaxLength input value up to 512 units  
    **Commands/OpenDialog**: will either perform defined commands or open a defined dialog window on save button press. [value] variable can be used to insert a value from a text field

#### Slider

```
  - Label: 'Max players %server_max_players%'
    Width: 250
    Start: 0
    End: '%cmi_equationint_{server_max_players}*2%'
    Step: 1
    InitialValue: '%server_max_players%'
    Commands:
    - 'cmi maxplayers [value]'
```

    Label: text to be shown on the left side of the value. Optional  
    **Permission**: permission node required to see and use input. Optional.  
    **Placeholder:** placeholder node required to use this input. Optional.  
    **Start**: starting value for the slider, can be fractional one like 2.5. Can be defined with a placeholder that returns a number value  
    **End**: end value for the slider. Can be defined with placeholder which returns a number value, for example ‘%cmi_equationint_{server_max_players}*2%’  
    **Step**: step size when the slider is adjusted, can be a fractional value or based on a placeholder  
    **InitialValue**: defines the place of the slider’s initial position; this should be in the range of Start and End. Can be defined with a placeholder like %server_max_players%  
    **Width:** width of slider  
    **Commands/OpenDialog**: will either perform defined commands or open a defined dialog window on save button press. [value] variable can be used to insert a value from a text field

## Buttons

```
  Buttons:
    Columns: 3 
    List:
    - Label: '{#red}Read The Rules' 
      Width: 100
      OpenDialog: license
      Tooltip: 
      - '{#red}Read it before starting to play!'
    - Label: '{#green>}Read a joke{#darkgreen<}'
      Width: 100
      OpenDialog: 'randomtext'
    - Label: '{#edward>}Start Playing{#darkgreen<}'
      Width: 100
      Commands: 
      - closeinv!
    - Label: '{#blue}Select a thing'
      Width: 100
      OpenDialog: inputexample
    - Label: ''
      Width: 1
    - Label: '{#yellow}Visual examples'
      Width: 100
      OpenDialog: visualexample
    - Label: '{#yellow}Visit Our Website %cmi_user_display_name%'
      Width: 304
      Url: www.zrips.net
```

    **Columns:** defines how many columns of buttons we should have; by default, it will be 1  
    **Permission**: permission node required to use this button. Optional. If the player doesn’t have access to the defined permission node, then the button will be either completely removed or replaced with 1pixel wide one which does nothing, 1pixel option only applies when you have multiple columns of buttons, just to preserve the format and look of the UI  
    **Placeholder:** placeholder node required to use this button. Optional.  
    **List:** list of buttons in defined order, keep in mind that buttons will be placed in exact order, and the number of buttons per line will always be fixed based on the previous Columns option. Only the last buttons get centered if their number is lower.  
        **Label:** label of button, can contain placeholders  
        **Tooltip:** tooltip to be shown when hovering over the button, this can contain color codes and placeholders. Can be made of more than one line, either separated with \\n or defined in a list  
        **Width:** button width, no less than 1, defaults to 250 if not set  
        **Commands:** one or multiple commands to be performed when clicking this button. Specialized commands supported. To use the player’s name inside a command, you can utilize the [playerName] variable  
        **OpenDialog:** an alternative option to commands that can open a different dialog from this one  
        **Url:** alternative option to commands, opens the provided URL link  
        **Save**: if indicated with a value of “true” like “Save: true”, then on button click, values entered into input fields will be processed. Keep in mind that this specific action will reopen the existing dialog window as its secondary action by default, so avoid defining OpenDialog with this entry to avoid unpredictable behavior. Commands can still be performed on the save button click, in addition to saving.

## Commands

```
/cmi dialogs buyitemexample [material]:oakwood
```

Commands can include custom variables which will be used inside dialogs, variable itself should be defined in specific format like **[customName]:variable** which simply means that any instance of **[customName]** will be replaced with **variable**

Check **basic shop** exampleMultiple variables can be defined if needed
