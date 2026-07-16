---
title: CMI 动态告示牌
tags: CMI, 告示牌
source: https://www.zrips.net/cmi/dynamic-signs/
---
![](https://www.zrips.net/wp-content/uploads/2019/02/2018-03-22_13-57-38.gif)

This is the feature which allows you to have signs with text which will update depending on set configuration. It can show text personalized for person who sees it or show general text for everyone. 

In example you can display player balance by adding appropriate placeholder, show items price he is holding currently and so on. 

Its not limited by what you can show and you can have more then 4 lines which will automatically scroll over them all.

To create Dynamic Sign, first of all place a sign on the ground. Text of the sign is not relevant and it can be completely blank.

[![](https://www.zrips.net/wp-content/uploads/2019/02/dsign1.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/dsign1.jpg)

Next, aim at sign and type in something like **/cmi dsign new didly**

You will notice particles appearing around you which will indicate area in which while player stands, sign will be updated. So player standing on another side will not cause unneeded load on server as we don’t need to update it while player cant see text

[![dsign2](https://www.zrips.net/wp-content/uploads/elementor/thumbs/dsign2-o3lnvg3o2f7chcsj5ioj1un8xdwhhe606dm19kpeyo.jpg "dsign2")](https://www.zrips.net/wp-content/uploads/2019/02/dsign2.jpg)

[![](https://www.zrips.net/wp-content/uploads/2019/02/dsign3-1024x446.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/dsign3.jpg)

After performing first command you will get char message which will looks something like this

[![](https://www.zrips.net/wp-content/uploads/2019/02/dsign4.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/dsign4.jpg)

Lets add some lines for it. In this example, lets make it to show items worth you are holding. Same you have have seen in top gif.

Click on [+] sign and lets enter first line

`%cmi_iteminhand_realname%`

This is CMI placeholder to show item name which player is holding currently. Click enter. Now next 2 lines should be something like  
`%cmi_iteminhand_amount%`

`&7Worth: %cmi_iteminhand_worth%`

End result should look like

[![](https://www.zrips.net/wp-content/uploads/2019/02/dsign6.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/dsign6.jpg)

[![](https://www.zrips.net/wp-content/uploads/2019/02/dsign7-1024x367.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/dsign7.jpg)

By default sign will only update once every 5 seconds. So lets change that for something more appropriate for this use.

Click no <Open gui> and you will get new window. Find a button which indicates update interval and change it to 1 second.

[![](https://www.zrips.net/wp-content/uploads/2019/02/dsign8.jpg)](https://www.zrips.net/wp-content/uploads/2019/02/dsign8.jpg)

That’s it. Sign is created and it will update automatically for everyone. 

Extra things. You can change sign behavior to be not an individual one. What that means that sign will not be updated for one single player for but everyone with appropriate information. This is more efficient way if you wan to show generic information for the players, like online count.

You can update sign update range, in case you have wider or narrower place where sign is located and you want to avoid its updating when its not needed. Default value if more then enough, but to have option is always nice.

Last line in Gif is from another feature: **[[CMI 交互命令|Interactive Commands]]**
