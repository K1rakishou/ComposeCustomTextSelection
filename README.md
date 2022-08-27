Default text selection API in Jetpack Compose is pretty bad because everything is hidden away from you and all the necessary classes/interfaces are made internal for some unknown reason which makes it impossible to, for example, change how text selection should appear (let's say by double-tapping the text instead of long-tapping which is usually used to display context menus). 
By default it's hardcoded to show up when you long tap the text. 
Also it's impossible to change selection cursors (only the color, but not the icons themselves). 
And you can't implement custom menu items (You can provide custom LocalTextToolbar but it can only have 4 hardcoded menus: copy, paste, cut, select all).
