Text selection API in Jetpack Compose is pretty bad because everything is hidden away from you and all the necessary classes/interfaces are made internal for some unknown reason so you can only do the bare minimum. Maybe it will be changed in the future but it's been 4 months since I suggested them to make some classes/interfaces public and they still haven't done that.

There are three problems in compose text selection which are solved by this library:

- Text selection is hardcoded to show up when you long tap the text. Sometimes, in lists especially, long tap might be used to show the context menu and in this case it makes sense to make the text selection appear on double tap. It's possible to accomplish in the Android View system (with some hacks) but impossible (at least I couldn't figure out how) in Compose. (Works).
- It's impossible to change selection cursors (only the color, but not the icons themselves). They are drawn as bitmaps so why not let us replace the bitmaps with our own implementations? (Not implemented yet).
- You can't implement custom menu items like in Android View system (You can provide custom LocalTextToolbar but it only has 4 hardcoded menus: copy, paste, cut, select all. Can't add your own). (Not implemented yet).

[<img src="sample.gif" width=400>](sample.gif)
