---
title: CMI 拾荒 Scavenge
tags: CMI, 扩展
source: https://www.zrips.net/cmi/extra/scavenge/
---
![](https://www.zrips.net/wp-content/uploads/2019/07/Scavanger2.gif)

With CMI you can break items into its original ingredients and get back enchantments as enchanted books. But every good thing has its own price.

By default item has base 8% chance to break. Then for each enchantment item has it will add 2% fail chance. So if you have 5 enchantments this will result into 10% extra fail chance which would be 18% in total. There is option to define max break chance in case you want to have atleast some change in getting item back even if you have 50 enchantments. In addition to this break chance can increase if item is damaged. By default this chance can increase up to 50% of extra fail chance if you have your durability at minimal amount.

Item ingredient extraction depends on item quality too. So if you have damaged item, you will get less ingredients back. This is separate from actual chance to get each ingredient. So in example if you have item with 100 max durability and 4 ingredients, while item is at 50 durability left, then with default configuration only 2 ingredients will have any chance to be extracted while other 2 will be completely ignored. So left 2 will have 25% chance being extracted and returned to player.

Enchantment extraction is more complex then this, as it can be main reason why you would want to scavenge item. Each enchantment will have 10% base fail chance, while each enchantment level adds extra 10% fail chance at max level, so enchantment like sharpness 3, which can have max level of 5, will have 16% fail chance.  
If enchantment extraction fails, it goes throw secondary check which can lower enchantment level and still give a enchantment book back to player. But if enchantment was at level 1, then in case it failed, then player will not get it. Secondary check by default has 50% base fail chance and adds extra 5% at max enchantment level. 

Scavenge process itself can have its own cost. This is just to prevent some possible exploits and make it worth while in doing so. Scavenge cost is determined by set base cost, which is 100, while adding defined percentage (5%) from items cost, which by itself is defined with setworth command, and adding enchantment(s) cost which again is defined with setenchantworth command. So this will have more dynamic process cost and will be adjusted accordingly from the item you are scavenging, so less worthy items will be cheaper while diamond ones with bunch of enchants will be most expensive ones.

Provided black list can block out some particular item from being scavanged or it can be turned into while list and only allow items defined in that list to be scavanged. So you can have better control on what you want to scavenge and what you want to block out.
