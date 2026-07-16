---
title: CMI 聊天室
tags: CMI, 扩展, 聊天
source: https://www.zrips.net/cmi/extra/chat-rooms/
---
## Introduction

Chat rooms allows you to create temporary or persistent places where players can talk in bigger groups. This is separate from general chat and provides more customization. Player can join only one chat room at the time, but he can see more than that

## Commands

```
/cmi chat create [chatRoomName] (-private) (-locked) (-persistent)
```

Creating chat rooms will require unique name of it, this doesn’t have any specific limitations on what it can be, only defines ID of this particular chat room

**-private** will make chat room only accessible with invitation, this will require you to have  **cmi.command.chat.create.private** permission node to create. Without this variable all chat rooms will be public and anyone will be able to join it

**-locked** Mainly used when you have to force players to remain in this chat room without them being able to leave it. Can be useful when you want to have staff “interrogation” room. You will need to have **cmi.command.chat.create.locked** permission node for it

**-persistent** Creates chat room which persists after server restart. By default chat rooms are temporary and will disappear either after server restart or after certain amount of time is passed when last user left that chat room. Requires **cmi.command.chat.create.persistent** permission node

```
/cmi chat join [chatRoomName]
```

Joins defined chat room. This is limited to chat rooms which are public, or you have invite, or you are owner of it. Limitations of joining can be bypassed with **cmi.command.chat.joinbypass** permission node

```
/cmi chat invite [playerName]
```

Sends invite to the player to join your current chat room. Player needs to have **cmi.command.chat.invite** permission node

```
/cmi chat leave
```

Leaves current chat room if you are in one. Player can’t leave **-locked** chat rooms unless they have **cmi.command.chat.leave.locked** permission node

```
/cmi chat kick [playerName]
```

Can kick players out of chat rooms. This can only be done by chat room owner and needs to have **cmi.command.chat.kick** permission node

```
/cmi chat force [playerName] (chatRoomName)
```

Can force player to join specific chat room. This will bypass players limitations to join chat room, but command sender needs to have **cmi.command.chat.force** or command needs to be sent from console.  
Chat room name is optional if command sender is player who is already in a chat room

```
/cmi chat see (chatRoomName)
```

Allows to see messages from defined chat room without joining it. You can’t see chat rooms you are not allowed to join. This command requires **cmi.command.chat.see** permission node, while **cmi.command.chat.seebypass** can allow you to see chat rooms you are not allowed to join.

```
/cmi chat unsee (chatRoomName)
```

Allows to unsee messages from defined chat room. This command requires **cmi.command.chat.see** permission node.

```
/cmi chat list
```

Lists all players in your current chat room. This requires **cmi.command.chat.list** permission node

```
/cmi chat listrooms
```

Lists all active chat rooms you can join. This requires **cmi.command.chat.listrooms** permission node
