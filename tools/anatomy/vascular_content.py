"""Named vascular structures from BodyParts3D 4.0, with a disjoint OBJ partition.

This explicit table includes artery/vein segments and trunks omitted by the
IS-A artery/vein classes. Broad pulmonary/coronary and fine distal branches are
grouped to keep draw calls manageable. It is not a complete microvascular atlas.
Only original coordinates are used; no vessel is generated or enlarged.
"""
import hashlib

# FMA/local identity | source name | French | display region | category | family | OBJ elements
_DATA = r"""
FMA3734|aorta|Aorte|thorax|arteries_trunk|vascular_aorta|FJ3411,FJ3413,FJ3427
FMA50039|right coronary artery|Artère coronaire droite et ses branches|thorax|coronary|vascular_coronary_artery|FJ2667,FJ2668,FJ2670,FJ2671,FJ2672,FJ2673,FJ2674,FJ2675,FJ2676,FJ2677,FJ2692,FJ2693,FJ2694,FJ2695,FJ2696,FJ2697,FJ2698,FJ2699,FJ2700,FJ2714,FJ2715,FJ2716,FJ2717,FJ2718,FJ2719,FJ2720,FJ2721,FJ2722,FJ2723
FMA50040|left coronary artery|Artère coronaire gauche et ses branches|thorax|coronary|vascular_coronary_artery|FJ2631,FJ2632,FJ2633,FJ2634,FJ2635,FJ2636,FJ2637,FJ2638,FJ2639,FJ2640,FJ2641,FJ2642,FJ2643,FJ2644,FJ2645,FJ2646,FJ2647,FJ2648,FJ2649,FJ2650,FJ2651,FJ2652,FJ2653,FJ2654,FJ2737
FMA50872|right pulmonary artery|Artère pulmonaire droite et ses branches|thorax|pulmonary|vascular_pulmonary_artery|FJ2041,FJ2044,FJ2967,FJ2968,FJ2969,FJ2970,FJ2971,FJ2972,FJ2973,FJ2974,FJ2975,FJ2976,FJ2977,FJ2978,FJ2979,FJ2980,FJ2981,FJ2982,FJ2983,FJ2984,FJ2985,FJ2986,FJ2987,FJ2988,FJ2989,FJ2990,FJ2991,FJ2992,FJ2993,FJ2994,FJ2995,FJ2996,FJ2997,FJ2998,FJ2999,FJ3000,FJ3001,FJ3002,FJ3003,FJ3004,FJ3005,FJ3006,FJ3007,FJ3008,FJ3009,FJ3010,FJ3011,FJ3012,FJ3013,FJ3014,FJ3015,FJ3016,FJ3017,FJ3018,FJ3019
FMA50873|left pulmonary artery|Artère pulmonaire gauche et ses branches|thorax|pulmonary|vascular_pulmonary_artery|FJ2881,FJ2882,FJ2883,FJ2884,FJ2885,FJ2886,FJ2887,FJ2888,FJ2889,FJ2890,FJ2891,FJ2892,FJ2893,FJ2894,FJ2895,FJ2902,FJ2903,FJ2904,FJ2905,FJ2906,FJ2907,FJ2908,FJ2909,FJ2910,FJ2911,FJ2912,FJ2913,FJ2914,FJ2915,FJ2916,FJ2917,FJ2918,FJ2919,FJ2920,FJ2921,FJ2922,FJ2923,FJ2924
FMA49914|right superior pulmonary vein|Veine pulmonaire supérieure droite et ses affluents|thorax|pulmonary|vascular_pulmonary_vein|FJ3020,FJ3021,FJ3022,FJ3023,FJ3024,FJ3025,FJ3026,FJ3027,FJ3028,FJ3029,FJ3030,FJ3031,FJ3032,FJ3033,FJ3034,FJ3035,FJ3036,FJ3037,FJ3038,FJ3039,FJ3042,FJ3053,FJ3064,FJ3067,FJ3068,FJ3069,FJ3070
FMA49916|left superior pulmonary vein|Veine pulmonaire supérieure gauche et ses affluents|thorax|pulmonary|vascular_pulmonary_vein|FJ2925,FJ2926,FJ2927,FJ2928,FJ2929,FJ2930,FJ2931,FJ2932,FJ2933,FJ2934,FJ2935,FJ2936,FJ2937,FJ2938,FJ2939,FJ2940,FJ2941,FJ2942,FJ2943,FJ2947,FJ2958,FJ2961,FJ2962,FJ2963,FJ2964,FJ2965
FMA49911|right inferior pulmonary vein|Veine pulmonaire inférieure droite et ses affluents|thorax|pulmonary|vascular_pulmonary_vein|FJ3040,FJ3041,FJ3043,FJ3044,FJ3045,FJ3046,FJ3047,FJ3048,FJ3049,FJ3050,FJ3051,FJ3052,FJ3054,FJ3055,FJ3056,FJ3057,FJ3058,FJ3059,FJ3060,FJ3061,FJ3062,FJ3063,FJ3065,FJ3066
FMA49913|left inferior pulmonary vein|Veine pulmonaire inférieure gauche et ses affluents|thorax|pulmonary|vascular_pulmonary_vein|FJ2944,FJ2945,FJ2946,FJ2948,FJ2949,FJ2950,FJ2951,FJ2952,FJ2953,FJ2954,FJ2955,FJ2956,FJ2957,FJ2959,FJ2960
FMA14778|right hepatic artery|Artère hépatique droite et ses branches|abdomen|arteries_trunk|vascular_artery|FJ1916,FJ3075,FJ3076,FJ3114,FJ3115,FJ3116,FJ3117
FMA14779|left hepatic artery|Artère hépatique gauche et ses branches|abdomen|arteries_trunk|vascular_artery|FJ3077,FJ3091,FJ3092,FJ3093,FJ3095,FJ3106,FJ3107
FMA15414|right portal vein|Branche droite de la veine porte et ses ramifications|abdomen|veins_trunk|vascular_portal|FJ1913,FJ1914,FJ2405,FJ3073,FJ3074,FJ3111,FJ3112,FJ3113,FJ3122
FMA15415|left portal vein|Branche gauche de la veine porte et ses ramifications|abdomen|veins_trunk|vascular_portal|FJ1893,FJ2404,FJ3102,FJ3124,FJ3125,FJ3126,FJ3127,FJ3128
FMA13921|branch of anterior interventricular branch of left coronary artery|Rameaux complémentaires de l’artère interventriculaire antérieure|thorax|coronary|vascular_coronary_artery|FJ2732,FJ2733,FJ2734
FMA50031|branch of anterior cerebral artery|Branches des artères cérébrales antérieures|skull|arteries_head|vascular_artery|FJ1655,FJ1671,FJ1671M,FJ1676,FJ1676M,FJ1681,FJ1681M,FJ1690,FJ1690M,FJ1696,FJ1696M,FJ1697,FJ1697M,FJ1698,FJ1698M,FJ1699,FJ1699M,FJ1722,FJ1722M
FMA50081|branch of middle cerebral artery|Branches des artères cérébrales moyennes|skull|arteries_head|vascular_artery|FJ1659,FJ1659M,FJ1662,FJ1662M,FJ1663,FJ1663M,FJ1664,FJ1664M,FJ1665,FJ1665M,FJ1666,FJ1666M,FJ1667,FJ1667M,FJ1668,FJ1668M,FJ1669,FJ1669M,FJ1670,FJ1670M,FJ1673,FJ1673M,FJ1685,FJ1685M,FJ1686,FJ1686M,FJ1693,FJ1693M,FJ1712,FJ1712M,FJ1716,FJ1716M,FJ1717,FJ1717M,FJ1724,FJ1724M,FJ1729,FJ1729M
FMA50586|branch of posterior cerebral artery|Branches des artères cérébrales postérieures|skull|arteries_head|vascular_artery|FJ1661,FJ1661M,FJ1675,FJ1675M,FJ1677,FJ1677M,FJ1678,FJ1678M,FJ1680,FJ1680M,FJ1687,FJ1687M,FJ1691,FJ1691M,FJ1720,FJ1720M,FJ1727,FJ1727M
FMA50576|branch of superior cerebellar artery|Branches des artères cérébelleuses supérieures|skull|arteries_head|vascular_artery|FJ1683,FJ1683M,FJ1688,FJ1688M,FJ1728,FJ1728M
FMA49893|coronary artery|Branches coronaires complémentaires|thorax|coronary|vascular_coronary_artery|FJ2735,FJ2736
FMA66326|pulmonary artery|Branches pulmonaires artérielles complémentaires|thorax|pulmonary|vascular_pulmonary_artery|FJ2896,FJ2897,FJ2898,FJ2899,FJ2900,FJ2901
FMA17541|tributary of hepatic vein|Affluents des veines hépatiques|abdomen|veins_trunk|vascular_vein|FJ1867,FJ3083,FJ3084,FJ3085,FJ3086,FJ3097,FJ3098,FJ3099,FJ3100,FJ3101,FJ3118,FJ3119,FJ3120,FJ3121
FMA63822|posterior intercostal arteries|Artères intercostales postérieures|thorax|arteries_trunk|vascular_artery|FJ1961,FJ1975
FMA70839|set of anterior intercostal veins|Veines intercostales antérieures|thorax|veins_trunk|vascular_vein|FJ1985,FJ1994
FMA22693|branch of brachial artery|Branches des artères brachiales|upper|arteries_upper|vascular_artery|FJ2215,FJ2225,FJ2261,FJ2262,FJ2267,FJ2277,FJ2331,FJ2362,FJ2373,FJ2374
FMA22738|branch of radial artery|Branches des artères radiales|upper|arteries_upper|vascular_artery|FJ2231,FJ2243,FJ2283,FJ2295,FJ2314,FJ2332,FJ2338,FJ2339,FJ2342,FJ2363,FJ2371,FJ2372
FMA22799|branch of ulnar artery|Branches des artères ulnaires|upper|arteries_upper|vascular_artery|FJ2214,FJ2223,FJ2236,FJ2241,FJ2245,FJ2259,FJ2266,FJ2275,FJ2288,FJ2293,FJ2297,FJ2311
FMA22842|branch of arterial anastomosis|Branches des arcades artérielles palmaires|hands|arteries_upper|vascular_artery|FJ2237,FJ2289,FJ2315,FJ2316,FJ2317,FJ2333,FJ2334,FJ2335,FJ2336,FJ2337,FJ2343,FJ2344,FJ2345,FJ2364,FJ2365,FJ2366,FJ2367,FJ2368,FJ2369,FJ2370
FMA22917|tributary of venous anastomosis|Affluents des arcades veineuses palmaires|hands|veins_upper|vascular_vein|FJ2186,FJ2199,FJ2319,FJ2321,FJ2322,FJ2324,FJ2325,FJ2326,FJ2327,FJ2328,FJ2329,FJ2340,FJ2349,FJ2351,FJ2352,FJ2354,FJ2355,FJ2356,FJ2357,FJ2358,FJ2359,FJ2360
FMA22937|tributary of brachial vein|Affluents des veines brachiales|upper|veins_upper|vascular_vein|FJ2238,FJ2244,FJ2260,FJ2290,FJ2296,FJ2312,FJ2320,FJ2323,FJ2350,FJ2353
FMA44324|tributary of deep femoral vein|Affluents des veines profondes de la cuisse|lower|veins_lower|vascular_vein|FJ2097,FJ2107,FJ2108,FJ2117,FJ2118,FJ2121,FJ2124,FJ2125,FJ2132,FJ2151,FJ2153,FJ2171,FJ2173,FJ2176,FJ2181,FJ2182,FJ2183,FJ2184,FJ2187,FJ2190,FJ2193,FJ2194,FJ2200
FMA44499|tributary of plantar venous arch|Affluents des arcades veineuses plantaires|feet|veins_lower|vascular_vein|FJ2113,FJ2115,FJ2160,FJ2165,FJ2191,FJ2207
FMA70815|set of perforating arteries|Artères perforantes du pied|feet|arteries_lower|vascular_artery|FJ2090,FJ2094,FJ2127,FJ2203
BP4_FMA70801_feet|set of dorsal digital arteries (feet)|Artères digitales dorsales des pieds|feet|arteries_lower|vascular_artery|FJ2093,FJ2197
BP4_FMA70801_hands|set of dorsal digital arteries (hands)|Artères digitales dorsales des mains|hands|arteries_upper|vascular_artery|FJ2318,FJ2346
FMA70800|set of dorsal metacarpal arteries|Artères métacarpiennes dorsales|hands|arteries_upper|vascular_artery|FJ2347,FJ2348
FMA70821|set of common plantar digital arteries|Artères digitales plantaires communes|feet|arteries_lower|vascular_artery|FJ2092,FJ2196
FMA71564|set of plantar digital arteries proper|Artères digitales plantaires propres|feet|arteries_lower|vascular_artery|FJ2095,FJ2204,FJ2205
FMA70919|set of plantar digital veins|Veines digitales plantaires|feet|veins_lower|vascular_vein|FJ2120,FJ2175
BP4_FMA70917_feet|set of dorsal digital veins (feet)|Veines digitales dorsales des pieds|feet|veins_lower|vascular_vein|FJ2185,FJ2198
BP4_FMA70922_feet|set of perforating veins (feet)|Veines perforantes des pieds|feet|veins_lower|vascular_vein|FJ2100,FJ2119,FJ2138,FJ2174
BP4_FMA70922_hands|set of perforating veins (hands)|Veines perforantes des mains|hands|veins_upper|vascular_vein|FJ2192
FMA5011|accessory hemiazygos vein|Veine hémi-azygos accessoire|thorax|veins_trunk|vascular_vein|FJ1981
FMA23068|acromial branch of right thoraco-acromial artery|Rameau acromial de l’artère thoraco-acromiale (droite)|upper|arteries_upper|vascular_artery|FJ2263
FMA23069|acromial branch of left thoraco-acromial artery|Rameau acromial de l’artère thoraco-acromiale (gauche)|upper|arteries_upper|vascular_artery|FJ2211
FMA76767|anterior cardiac vein|Veines cardiaques antérieures|thorax|coronary|vascular_coronary_vein|FJ2725,FJ2730
FMA14816|anterior cecal artery|Artère cæcale antérieure|abdomen|arteries_trunk|vascular_artery|FJ3406
FMA50029|right anterior cerebral artery|Artère cérébrale antérieure (droite)|skull|arteries_head|vascular_artery|FJ1654
FMA50030|left anterior cerebral artery|Artère cérébrale antérieure (gauche)|skull|arteries_head|vascular_artery|FJ1654M
FMA50088|right anterior choroidal artery|Artère choroïdienne antérieure (droite)|skull|arteries_head|vascular_artery|FJ1658
FMA50089|left anterior choroidal artery|Artère choroïdienne antérieure (gauche)|skull|arteries_head|vascular_artery|FJ1658M
FMA22682|right anterior circumflex humeral artery|Artère circonflexe humérale antérieure (droite)|upper|arteries_upper|vascular_artery|FJ2264
FMA22683|left anterior circumflex humeral artery|Artère circonflexe humérale antérieure (gauche)|upper|arteries_upper|vascular_artery|FJ2212
FMA77954|right anterior circumflex humeral vein|Veine circonflexe humérale antérieure (droite)|upper|veins_upper|vascular_vein|FJ2265
FMA77955|left anterior circumflex humeral vein|Veine circonflexe humérale antérieure (gauche)|upper|veins_upper|vascular_vein|FJ2213
FMA70486|anterior division of right renal artery|Division antérieure de l’artère rénale (droite)|abdomen|arteries_trunk|vascular_artery|FJ3558,FJ3559,FJ3560,FJ3561,FJ3562,FJ3563
FMA70487|anterior division of left renal artery|Division antérieure de l’artère rénale (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3458,FJ3459,FJ3460,FJ3461,FJ3462,FJ3463
FMA50544|anterior inferior cerebellar artery|Artère cérébelleuse antéro-inférieure|skull|arteries_head|vascular_artery|FJ1656,FJ1656M
FMA70479|anterior inferior pancreaticoduodenal artery|Artère pancréatico-duodénale antéro-inférieure|abdomen|arteries_trunk|vascular_artery|FJ3401
FMA66403|anterior interventricular vein|Veine interventriculaire antérieure|thorax|coronary|vascular_coronary_vein|FJ2657,FJ2658,FJ2659,FJ2660,FJ2661,FJ2662,FJ2663,FJ2664,FJ2665
FMA50532|right anterior spinal artery|Artère spinale antérieure (droite)|neck|arteries_head|vascular_artery|FJ1657
FMA50533|left anterior spinal artery|Artère spinale antérieure (gauche)|neck|arteries_head|vascular_artery|FJ1657M
FMA14782|anterior superior pancreaticoduodenal artery|Artère pancréatico-duodénale antéro-supérieure|abdomen|arteries_trunk|vascular_artery|FJ3409
FMA43896|right anterior tibial artery|Artère tibiale antérieure (droite)|lower|arteries_lower|vascular_artery|FJ2130
FMA43897|left anterior tibial artery|Artère tibiale antérieure (gauche)|lower|arteries_lower|vascular_artery|FJ2065
FMA43907|right anterior tibial recurrent artery|Artère récurrente tibiale antérieure (droite)|lower|arteries_lower|vascular_artery|FJ2131
FMA43908|left anterior tibial recurrent artery|Artère récurrente tibiale antérieure (gauche)|lower|arteries_lower|vascular_artery|FJ2066
FMA14818|appendicular artery|Artère appendiculaire|abdomen|arteries_trunk|vascular_artery|FJ3410
FMA69494|right arcuate artery|Artère arquée du pied (droite)|feet|arteries_lower|vascular_artery|FJ2133
FMA69495|left arcuate artery|Artère arquée du pied (gauche)|feet|arteries_lower|vascular_artery|FJ2067
FMA14828|ascending branch of left colic artery|Rameau ascendant de l’artère colique (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3399
FMA14820|ascending branch of inferior branch of ileocolic artery|Rameau ascendant de la branche inférieure de l’artère iléo-colique|abdomen|arteries_trunk|vascular_artery|FJ3414
FMA12858|ascending lumbar vein|Veine lombaire ascendante|abdomen|veins_trunk|vascular_vein|FJ3658
FMA4843|right ascending lumbar vein|Veine lombaire ascendante (droite)|abdomen|veins_trunk|vascular_vein|FJ3493
FMA4950|left ascending lumbar vein|Veine lombaire ascendante (gauche)|abdomen|veins_trunk|vascular_vein|FJ3589
FMA22655|right axillary artery|Artère axillaire (droite)|upper|arteries_upper|vascular_artery|FJ2268
FMA22656|left axillary artery|Artère axillaire (gauche)|upper|arteries_upper|vascular_artery|FJ2216
FMA13330|right axillary vein|Veine axillaire (droite)|upper|veins_upper|vascular_vein|FJ2269
FMA13331|left axillary vein|Veine axillaire (gauche)|upper|veins_upper|vascular_vein|FJ2217
FMA4838|azygos vein|Veine azygos|thorax|veins_trunk|vascular_vein|FJ3416
FMA50542|basilar artery|Artère basilaire|skull|arteries_head|vascular_artery|FJ1672
FMA22909|right basilic vein|Veine basilique (droite)|upper|veins_upper|vascular_vein|FJ2270
FMA22910|left basilic vein|Veine basilique (gauche)|upper|veins_upper|vascular_vein|FJ2218
FMA22691|right brachial artery|Artère brachiale (droite)|upper|arteries_upper|vascular_artery|FJ2271
FMA22692|left brachial artery|Artère brachiale (gauche)|upper|arteries_upper|vascular_artery|FJ2219
FMA3932|brachiocephalic artery|Tronc brachio-céphalique|thorax|arteries_trunk|vascular_artery|FJ3417
FMA4751|right brachiocephalic vein|Veine brachio-céphalique (droite)|thorax|veins_trunk|vascular_vein|FJ3583
FMA4761|left brachiocephalic vein|Veine brachio-céphalique (gauche)|thorax|veins_trunk|vascular_vein|FJ3482
FMA50146|branch of right anterior choroidal artery to posterior limb of right internal capsule|Rameau de l’artère choroïdienne antérieure destiné au bras postérieur de la capsule interne (droite)|skull|arteries_head|vascular_artery|FJ1674
FMA50147|branch of left anterior choroidal artery to posterior limb of left internal capsule|Rameau de l’artère choroïdienne antérieure destiné au bras postérieur de la capsule interne (gauche)|skull|arteries_head|vascular_artery|FJ1674M
FMA68109|bronchial artery|Artère bronchique|thorax|arteries_trunk|vascular_artery|FJ1933
FMA14793|caudal pancreatic artery|Artère de la queue du pancréas|abdomen|arteries_trunk|vascular_artery|FJ3419
FMA14812|celiac trunk|Tronc cœliaque|abdomen|arteries_trunk|vascular_artery|FJ3421
FMA13325|right cephalic vein|Veine céphalique (droite)|upper|veins_upper|vascular_vein|FJ2272
FMA13326|left cephalic vein|Veine céphalique (gauche)|upper|veins_upper|vascular_vein|FJ2220
FMA23180|right circumflex scapular artery|Artère circonflexe de la scapula (droite)|upper|arteries_upper|vascular_artery|FJ2273
FMA23181|left circumflex scapular artery|Artère circonflexe de la scapula (gauche)|upper|arteries_upper|vascular_artery|FJ2221
FMA77949|right circumflex scapular vein|Veine circonflexe de la scapula (droite)|upper|veins_upper|vascular_vein|FJ2274
FMA77950|left circumflex scapular vein|Veine circonflexe de la scapula (gauche)|upper|veins_upper|vascular_vein|FJ2222
FMA14811|right colic artery|Artère colique (droite)|abdomen|arteries_trunk|vascular_artery|FJ3590
FMA14826|left colic artery|Artère colique (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3494
FMA15394|left colic vein|Veine colique (gauche)|abdomen|veins_trunk|vascular_vein|FJ3400,FJ3429,FJ3495
FMA15407|right colic vein|Veine colique (droite)|abdomen|veins_trunk|vascular_vein|FJ3591
FMA3941|right common carotid artery|Artère carotide commune (droite)|neck|arteries_head|vascular_artery|FJ3564
FMA4058|left common carotid artery|Artère carotide commune (gauche)|neck|arteries_head|vascular_artery|FJ3483
FMA14771|common hepatic artery|Artère hépatique commune|abdomen|arteries_trunk|vascular_artery|FJ3078
FMA14765|right common iliac artery|Artère iliaque commune (droite)|pelvis|arteries_trunk|vascular_artery|FJ3565
FMA14766|left common iliac artery|Artère iliaque commune (gauche)|pelvis|arteries_trunk|vascular_artery|FJ3464
FMA21387|right common iliac vein|Veine iliaque commune (droite)|pelvis|veins_trunk|vascular_vein|FJ3566
FMA21388|left common iliac vein|Veine iliaque commune (gauche)|pelvis|veins_trunk|vascular_vein|FJ3465
FMA4706|coronary sinus|Sinus coronaire|thorax|coronary|vascular_coronary_vein|FJ2655
FMA4086|left costocervical trunk|Tronc costo-cervical (gauche)|neck|arteries_head|vascular_artery|FJ2224
FMA5039|right costocervical trunk|Tronc costo-cervical (droite)|neck|arteries_head|vascular_artery|FJ2276
FMA10660|right deep cervical artery|Artère cervicale profonde (droite)|neck|arteries_head|vascular_artery|FJ2278
FMA4134|left deep cervical artery|Artère cervicale profonde (gauche)|neck|arteries_head|vascular_artery|FJ2226
FMA21354|deep dorsal vein of penis|Veine dorsale profonde du pénis|pelvis|veins_trunk|vascular_vein|FJ2056
FMA51042|right deep femoral vein|Veine profonde de la cuisse (droite)|lower|veins_lower|vascular_vein|FJ2135
FMA51043|left deep femoral vein|Veine profonde de la cuisse (gauche)|lower|veins_lower|vascular_vein|FJ2099
FMA22912|right deep palmar venous arch|Arcade veineuse palmaire profonde (droite)|hands|veins_upper|vascular_vein|FJ2281
FMA22913|left deep palmar venous arch|Arcade veineuse palmaire profonde (gauche)|hands|veins_upper|vascular_vein|FJ2229
FMA69514|right deep plantar artery|Artère plantaire profonde (droite)|feet|arteries_lower|vascular_artery|FJ2136
FMA69515|left deep plantar artery|Artère plantaire profonde (gauche)|feet|arteries_lower|vascular_artery|FJ2068
FMA23072|deltoid branch of right thoraco-acromial artery|Rameau deltoïdien de l’artère thoraco-acromiale (droite)|upper|arteries_upper|vascular_artery|FJ2282
FMA23073|deltoid branch of left thoraco-acromial artery|Rameau deltoïdien de l’artère thoraco-acromiale (gauche)|upper|arteries_upper|vascular_artery|FJ2230
FMA14829|descending branch of left colic artery|Rameau descendant de l’artère colique (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3428
FMA21422|descending branch of right lateral circumflex femoral artery|Rameau descendant de l’artère circonflexe latérale de la cuisse (droite)|lower|arteries_lower|vascular_artery|FJ2057
FMA21423|descending branch of left lateral circumflex femoral artery|Rameau descendant de l’artère circonflexe latérale de la cuisse (gauche)|lower|arteries_lower|vascular_artery|FJ2063
FMA22507|right descending genicular artery|Artère descendante du genou (droite)|lower|arteries_lower|vascular_artery|FJ2137
FMA22508|left descending genicular artery|Artère descendante du genou (gauche)|lower|arteries_lower|vascular_artery|FJ2069
FMA69713|digital artery of foot|Artère digitale du pied|feet|arteries_lower|vascular_artery|FJ2072,FJ2141
FMA69517|distal perforating artery|Artère perforante distale|feet|arteries_lower|vascular_artery|FJ2058,FJ2064
FMA20818|right dorsal artery of penis|Artère dorsale du pénis (droite)|pelvis|arteries_trunk|vascular_artery|FJ3592,FJ3593
FMA20819|left dorsal artery of penis|Artère dorsale du pénis (gauche)|pelvis|arteries_trunk|vascular_artery|FJ3496,FJ3497
FMA14787|dorsal pancreatic artery|Artère pancréatique dorsale|abdomen|arteries_trunk|vascular_artery|FJ3430
FMA10552|left dorsal scapular artery|Artère dorsale de la scapula (gauche)|upper|arteries_upper|vascular_artery|FJ2232
FMA4057|right dorsal scapular artery|Artère dorsale de la scapula (droite)|upper|arteries_upper|vascular_artery|FJ2284
FMA44881|dorsal venous arch of right foot|Arcade veineuse dorsale du pied (droite)|feet|veins_lower|vascular_vein|FJ2061,FJ2062
FMA44882|dorsal venous arch of left foot|Arcade veineuse dorsale du pied (gauche)|feet|veins_lower|vascular_vein|FJ2059,FJ2060
FMA62506|dorsal venous network of right hand|Réseau veineux dorsal de la main (droite)|hands|veins_upper|vascular_vein|FJ2280
FMA62507|dorsal venous network of left hand|Réseau veineux dorsal de la main (gauche)|hands|veins_upper|vascular_vein|FJ2228
FMA43916|right dorsalis pedis artery|Artère dorsale du pied (droite)|feet|arteries_lower|vascular_artery|FJ2055
FMA43917|left dorsalis pedis artery|Artère dorsale du pied (gauche)|feet|arteries_lower|vascular_artery|FJ2073
FMA4149|esophageal artery|Artère œsophagienne|thorax|arteries_trunk|vascular_artery|FJ1934
FMA18806|right external iliac artery|Artère iliaque externe (droite)|pelvis|arteries_trunk|vascular_artery|FJ3567
FMA18807|left external iliac artery|Artère iliaque externe (gauche)|pelvis|arteries_trunk|vascular_artery|FJ3466
FMA18885|right external iliac vein|Veine iliaque externe (droite)|pelvis|veins_trunk|vascular_vein|FJ3568
FMA18886|left external iliac vein|Veine iliaque externe (gauche)|pelvis|veins_trunk|vascular_vein|FJ3484,FJ3522,FJ3523,FJ3524
FMA70249|right femoral artery|Artère fémorale (droite)|lower|arteries_lower|vascular_artery|FJ2143
FMA70250|left femoral artery|Artère fémorale (gauche)|lower|arteries_lower|vascular_artery|FJ2074
FMA21188|right femoral vein|Veine fémorale (droite)|lower|veins_lower|vascular_vein|FJ2144
FMA21189|left femoral vein|Veine fémorale (gauche)|lower|veins_lower|vascular_vein|FJ2102
FMA66242|right first posterior intercostal artery|Première artère intercostale postérieure (droite)|thorax|arteries_trunk|vascular_artery|FJ1939
FMA66243|left first posterior intercostal artery|Première artère intercostale postérieure (gauche)|thorax|arteries_trunk|vascular_artery|FJ1973
FMA14768|left gastric artery|Artère gastrique (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3499
FMA14776|right gastric artery|Artère gastrique (droite)|abdomen|arteries_trunk|vascular_artery|FJ3594
FMA15399|left gastric vein|Veine gastrique (gauche)|abdomen|veins_trunk|vascular_vein|FJ3500
FMA15400|right gastric vein|Veine gastrique (droite)|abdomen|veins_trunk|vascular_vein|FJ3595
FMA14781|right gastro-epiploic artery|Artère gastro-omentale (droite)|abdomen|arteries_trunk|vascular_artery|FJ3596
FMA14796|left gastro-epiploic artery|Artère gastro-omentale (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3501
FMA15390|left gastroepiploic vein|Veine gastro-omentale (gauche)|abdomen|veins_trunk|vascular_vein|FJ3502
FMA15397|right gastroepiploic vein|Veine gastro-omentale (droite)|abdomen|veins_trunk|vascular_vein|FJ3597
FMA4707|great cardiac vein|Grande veine du cœur|thorax|coronary|vascular_coronary_vein|FJ2656
FMA14792|great pancreatic artery|Grande artère pancréatique|abdomen|arteries_trunk|vascular_artery|FJ3433
FMA21379|right great saphenous vein|Grande veine saphène (droite)|lower|veins_lower|vascular_vein|FJ2145
FMA21380|left great saphenous vein|Grande veine saphène (gauche)|lower|veins_lower|vascular_vein|FJ2103
FMA4944|hemiazygos vein|Veine hémi-azygos|thorax|veins_trunk|vascular_vein|FJ3434
FMA14772|hepatic artery proper|Artère hépatique propre|abdomen|arteries_trunk|vascular_artery|FJ3081
FMA14338|right hepatic vein|Veine hépatique (droite)|abdomen|veins_trunk|vascular_vein|FJ2416
FMA14339|left hepatic vein|Veine hépatique (gauche)|abdomen|veins_trunk|vascular_vein|FJ2415
FMA50428|hypothalamic branch of right posterior communicating artery|Rameau hypothalamique de l’artère communicante postérieure (droite)|skull|arteries_head|vascular_artery|FJ1679
FMA50429|hypothalamic branch of left posterior communicating artery|Rameau hypothalamique de l’artère communicante postérieure (gauche)|skull|arteries_head|vascular_artery|FJ1679M
FMA14809|ileal artery|Artère iléale|abdomen|arteries_trunk|vascular_artery|FJ3437
FMA15405|ileal vein|Veine iléale|abdomen|veins_trunk|vascular_vein|FJ3438
FMA14815|ileocolic artery|Artère iléo-colique|abdomen|arteries_trunk|vascular_artery|FJ3439
FMA15408|ileocolic vein|Veine iléo-colique|abdomen|veins_trunk|vascular_vein|FJ3415,FJ3440
FMA18903|right iliolumbar vein|Veine ilio-lombaire (droite)|pelvis|veins_trunk|vascular_vein|FJ3603
FMA18904|left iliolumbar vein|Veine ilio-lombaire (gauche)|pelvis|veins_trunk|vascular_vein|FJ3510
FMA20688|right inferior epigastric artery|Artère épigastrique inférieure (droite)|abdomen|arteries_trunk|vascular_artery|FJ3604
FMA20689|left inferior epigastric artery|Artère épigastrique inférieure (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3511
FMA21163|right inferior epigastric vein|Veine épigastrique inférieure (droite)|abdomen|veins_trunk|vascular_vein|FJ3605
FMA21164|left inferior epigastric vein|Veine épigastrique inférieure (gauche)|abdomen|veins_trunk|vascular_vein|FJ3512
FMA18912|right inferior gluteal vein|Veine glutéale inférieure (droite)|pelvis|veins_trunk|vascular_vein|FJ3606
FMA18913|left inferior gluteal vein|Veine glutéale inférieure (gauche)|pelvis|veins_trunk|vascular_vein|FJ3513
FMA43892|right inferior lateral genicular artery|Artère inféro-latérale du genou (droite)|lower|arteries_lower|vascular_artery|FJ2150
FMA43893|left inferior lateral genicular artery|Artère inféro-latérale du genou (gauche)|lower|arteries_lower|vascular_artery|FJ2076
FMA43890|right inferior medial genicular artery|Artère inféro-médiale du genou (droite)|lower|arteries_lower|vascular_artery|FJ2152
FMA43891|left inferior medial genicular artery|Artère inféro-médiale du genou (gauche)|lower|arteries_lower|vascular_artery|FJ2077
FMA14750|inferior mesenteric artery|Artère mésentérique inférieure|abdomen|arteries_trunk|vascular_artery|FJ3442
FMA15391|inferior mesenteric vein|Veine mésentérique inférieure|abdomen|veins_trunk|vascular_vein|FJ3443
FMA14790|inferior pancreatic artery|Artère pancréatique inférieure|abdomen|arteries_trunk|vascular_artery|FJ3444
FMA14805|inferior pancreaticoduodenal artery|Artère pancréatico-duodénale inférieure|abdomen|arteries_trunk|vascular_artery|FJ3446
FMA14746|right inferior phrenic artery|Artère phrénique inférieure (droite)|abdomen|arteries_trunk|vascular_artery|FJ3619,FJ3620,FJ3621,FJ3622,FJ3623,FJ3624,FJ3625,FJ3626
FMA14747|left inferior phrenic artery|Artère phrénique inférieure (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3514,FJ3515,FJ3516,FJ3517,FJ3518,FJ3519,FJ3520
FMA68068|inferior phrenic vein|Veine phrénique inférieure|abdomen|veins_trunk|vascular_vein|FJ3447,FJ3448,FJ3449,FJ3450,FJ3451,FJ3452
FMA86346|inferior segmental branch of right renal artery|Rameau segmentaire inférieur de l’artère rénale (droite)|abdomen|arteries_trunk|vascular_artery|FJ2043
FMA86347|inferior segmental branch of left renal artery|Rameau segmentaire inférieur de l’artère rénale (gauche)|abdomen|arteries_trunk|vascular_artery|FJ2049
FMA69265|right inferior suprarenal artery|Artère surrénale inférieure (droite)|abdomen|arteries_trunk|vascular_artery|FJ3584
FMA69266|left inferior suprarenal artery|Artère surrénale inférieure (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3467
FMA10680|left inferior thyroid artery|Artère thyroïdienne inférieure (gauche)|neck|arteries_head|vascular_artery|FJ2210
FMA10697|right inferior thyroid artery|Artère thyroïdienne inférieure (droite)|neck|arteries_head|vascular_artery|FJ2209
FMA10951|inferior vena cava|Veine cave inférieure|abdomen|veins_trunk|vascular_vein|FJ3441,FJ3659
FMA50369|insular part of right middle cerebral artery|Segment insulaire de l’artère cérébrale moyenne (droite)|skull|arteries_head|vascular_artery|FJ1660,FJ1694
FMA50370|insular part of left middle cerebral artery|Segment insulaire de l’artère cérébrale moyenne (gauche)|skull|arteries_head|vascular_artery|FJ1660M,FJ1694M
FMA3949|right internal carotid artery|Artère carotide interne (droite)|neck|arteries_head|vascular_artery|FJ1682
FMA4062|left internal carotid artery|Artère carotide interne (gauche)|neck|arteries_head|vascular_artery|FJ1682M
FMA18809|right internal iliac artery|Artère iliaque interne (droite)|pelvis|arteries_trunk|vascular_artery|FJ3569
FMA18810|left internal iliac artery|Artère iliaque interne (gauche)|pelvis|arteries_trunk|vascular_artery|FJ3468
FMA18887|right internal iliac vein|Veine iliaque interne (droite)|pelvis|veins_trunk|vascular_vein|FJ3570,FJ3571,FJ3572,FJ3607,FJ3608,FJ3609
FMA18888|left internal iliac vein|Veine iliaque interne (gauche)|pelvis|veins_trunk|vascular_vein|FJ3469,FJ3470,FJ3471
FMA4754|right internal jugular vein|Veine jugulaire interne (droite)|neck|veins_head|vascular_vein|FJ3585
FMA4762|left internal jugular vein|Veine jugulaire interne (gauche)|neck|veins_head|vascular_vein|FJ3485
FMA18918|right internal pudendal vein|Veine pudendale interne (droite)|pelvis|veins_trunk|vascular_vein|FJ3610
FMA18919|left internal pudendal vein|Veine pudendale interne (gauche)|pelvis|veins_trunk|vascular_vein|FJ3525
FMA3969|right internal thoracic artery|Artère thoracique interne (droite)|thorax|arteries_trunk|vascular_artery|FJ1937
FMA4068|left internal thoracic artery|Artère thoracique interne (gauche)|thorax|arteries_trunk|vascular_artery|FJ1972
FMA4729|internal thoracic vein|Veine thoracique interne|thorax|veins_trunk|vascular_vein|FJ1993
FMA50568|lateral branch of right pontine artery|Rameau latéral de l’artère pontique (droite)|skull|arteries_head|vascular_artery|FJ1684
FMA50569|lateral branch of left pontine artery|Rameau latéral de l’artère pontique (gauche)|skull|arteries_head|vascular_artery|FJ1684M
FMA20801|right lateral circumflex femoral artery|Artère circonflexe latérale de la cuisse (droite)|lower|arteries_lower|vascular_artery|FJ2158
FMA20802|left lateral circumflex femoral artery|Artère circonflexe latérale de la cuisse (gauche)|lower|arteries_lower|vascular_artery|FJ2078
FMA44920|right lateral circumflex femoral vein|Veine circonflexe latérale de la cuisse (droite)|lower|veins_lower|vascular_vein|FJ2201
FMA44922|left lateral circumflex femoral vein|Veine circonflexe latérale de la cuisse (gauche)|lower|veins_lower|vascular_vein|FJ2188
FMA43931|right lateral plantar artery|Artère plantaire latérale (droite)|feet|arteries_lower|vascular_artery|FJ2159
FMA43932|left lateral plantar artery|Artère plantaire latérale (gauche)|feet|arteries_lower|vascular_artery|FJ2079
FMA18906|right lateral sacral vein|Veine sacrale latérale (droite)|pelvis|veins_trunk|vascular_vein|FJ3611
FMA18907|left lateral sacral vein|Veine sacrale latérale (gauche)|pelvis|veins_trunk|vascular_vein|FJ3526
FMA22588|right lateral superior genicular artery|Artère supéro-latérale du genou (droite)|lower|arteries_lower|vascular_artery|FJ2162
FMA22589|left lateral superior genicular artery|Artère supéro-latérale du genou (gauche)|lower|arteries_lower|vascular_artery|FJ2080
FMA69490|right lateral tarsal artery|Artère tarsienne latérale (droite)|feet|arteries_lower|vascular_artery|FJ2163
FMA69491|left lateral tarsal artery|Artère tarsienne latérale (gauche)|feet|arteries_lower|vascular_artery|FJ2081
FMA22675|right lateral thoracic artery|Artère thoracique latérale (droite)|thorax|arteries_trunk|vascular_artery|FJ1938
FMA22676|left lateral thoracic artery|Artère thoracique latérale (gauche)|thorax|arteries_trunk|vascular_artery|FJ1976
FMA71211|right lateral thoracic vein|Veine thoracique latérale (droite)|thorax|veins_trunk|vascular_vein|FJ2285
FMA71212|left lateral thoracic vein|Veine thoracique latérale (gauche)|thorax|veins_trunk|vascular_vein|FJ2233
FMA71708|right lobe branch of right hepatic artery|Rameau lobaire de l’artère hépatique (droite)|abdomen|arteries_trunk|vascular_artery|FJ1924
FMA71710|left lobe branch of left hepatic artery|Rameau lobaire de l’artère hépatique (gauche)|abdomen|arteries_trunk|vascular_artery|FJ1874
FMA14735|lumbar artery|Artère lombaire|abdomen|arteries_trunk|vascular_artery|FJ3632,FJ3636
FMA15370|lumbar vein|Veine lombaire|abdomen|veins_trunk|vascular_vein|FJ3631,FJ3635
FMA14831|marginal artery of colon|Artère marginale du côlon|abdomen|arteries_trunk|vascular_artery|FJ3534
FMA4708|left marginal vein|Veine marginale du cœur (gauche)|thorax|coronary|vascular_coronary_vein|FJ2703,FJ2704,FJ2705
FMA4716|right marginal vein|Veine marginale du cœur (droite)|thorax|coronary|vascular_coronary_vein|FJ2727,FJ2728,FJ2729
FMA22935|right medial brachial vein|Veine brachiale médiale (droite)|upper|veins_upper|vascular_vein|FJ2341
FMA22936|left medial brachial vein|Veine brachiale médiale (gauche)|upper|veins_upper|vascular_vein|FJ2313
FMA50566|medial branch of right pontine artery|Rameau médial de l’artère pontique (droite)|skull|arteries_head|vascular_artery|FJ1689
FMA50567|medial branch of left pontine artery|Rameau médial de l’artère pontique (gauche)|skull|arteries_head|vascular_artery|FJ1689M
FMA44918|right medial circumflex femoral vein|Veine circonflexe médiale de la cuisse (droite)|lower|veins_lower|vascular_vein|FJ2202
FMA44919|left medial circumflex femoral vein|Veine circonflexe médiale de la cuisse (gauche)|lower|veins_lower|vascular_vein|FJ2189
FMA43929|right medial plantar artery|Artère plantaire médiale (droite)|feet|arteries_lower|vascular_artery|FJ2164
FMA43930|left medial plantar artery|Artère plantaire médiale (gauche)|feet|arteries_lower|vascular_artery|FJ2082
FMA22586|right medial superior genicular artery|Artère supéro-médiale du genou (droite)|lower|arteries_lower|vascular_artery|FJ2166
FMA22587|left medial superior genicular artery|Artère supéro-médiale du genou (gauche)|lower|arteries_lower|vascular_artery|FJ2083
FMA22968|right median antebrachial vein|Veine médiane de l’avant-bras (droite)|upper|veins_upper|vascular_vein|FJ2286
FMA22969|left median antebrachial vein|Veine médiane de l’avant-bras (gauche)|upper|veins_upper|vascular_vein|FJ2234
FMA22964|right median cubital vein|Veine médiane du coude (droite)|upper|veins_upper|vascular_vein|FJ2287
FMA22965|left median cubital vein|Veine médiane du coude (gauche)|upper|veins_upper|vascular_vein|FJ2235
FMA77168|median sacral vein|Veine sacrale médiane|pelvis|veins_trunk|vascular_vein|FJ3541
FMA4713|middle cardiac vein|Veine moyenne du cœur|thorax|coronary|vascular_coronary_vein|FJ2678,FJ2679,FJ2680,FJ2681,FJ2682,FJ2683,FJ2684,FJ2685,FJ2686,FJ2687,FJ2688,FJ2689,FJ2690,FJ2691
FMA14810|middle colic artery|Artère colique moyenne|abdomen|arteries_trunk|vascular_artery|FJ3542
FMA15406|middle colic vein|Veine colique moyenne|abdomen|veins_trunk|vascular_vein|FJ3543
FMA22562|right middle genicular artery|Artère moyenne du genou (droite)|lower|arteries_lower|vascular_artery|FJ2167
FMA22563|left middle genicular artery|Artère moyenne du genou (gauche)|lower|arteries_lower|vascular_artery|FJ2084
FMA14340|middle hepatic vein|Veine hépatique moyenne|abdomen|veins_trunk|vascular_vein|FJ2414
FMA14755|right middle suprarenal artery|Artère surrénale moyenne (droite)|abdomen|arteries_trunk|vascular_artery|FJ3586
FMA14756|left middle suprarenal artery|Artère surrénale moyenne (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3472
FMA10692|right musculophrenic artery|Artère musculo-phrénique (droite)|thorax|arteries_trunk|vascular_artery|FJ1969
FMA4077|left musculophrenic artery|Artère musculo-phrénique (gauche)|thorax|arteries_trunk|vascular_artery|FJ1979
FMA4772|right musculophrenic vein|Veine musculo-phrénique (droite)|thorax|veins_trunk|vascular_vein|FJ1996
FMA4786|left musculophrenic vein|Veine musculo-phrénique (gauche)|thorax|veins_trunk|vascular_vein|FJ1988
FMA18915|right obturator vein|Veine obturatrice (droite)|pelvis|veins_trunk|vascular_vein|FJ3612
FMA18916|left obturator vein|Veine obturatrice (gauche)|pelvis|veins_trunk|vascular_vein|FJ3527
FMA49869|right ophthalmic artery|Artère ophtalmique (droite)|skull|arteries_head|vascular_artery|FJ1695
FMA49870|left ophthalmic artery|Artère ophtalmique (gauche)|skull|arteries_head|vascular_artery|FJ1695M
FMA15398|pancreaticoduodenal vein|Veine pancréatico-duodénale|abdomen|veins_trunk|vascular_vein|FJ3545,FJ3646,FJ3655
FMA23063|pectoral branch of right thoraco-acromial artery|Rameau pectoral de l’artère thoraco-acromiale (droite)|upper|arteries_upper|vascular_artery|FJ2361
FMA23064|pectoral branch of left thoraco-acromial artery|Rameau pectoral de l’artère thoraco-acromiale (gauche)|upper|arteries_upper|vascular_artery|FJ2330
FMA43943|right plantar arch|Arcade artérielle plantaire (droite)|feet|arteries_lower|vascular_artery|FJ2169
FMA43944|left plantar arch|Arcade artérielle plantaire (gauche)|feet|arteries_lower|vascular_artery|FJ2085
FMA43956|plantar metatarsal artery|Artère métatarsienne plantaire|feet|arteries_lower|vascular_artery|FJ2096,FJ2206
FMA44883|plantar venous arch of right foot|Arcade veineuse plantaire du pied (droite)|feet|veins_lower|vascular_vein|FJ2129
FMA44884|plantar venous arch of left foot|Arcade veineuse plantaire du pied (gauche)|feet|veins_lower|vascular_vein|FJ2128
FMA77380|right popliteal artery|Artère poplitée (droite)|lower|arteries_lower|vascular_artery|FJ2170
FMA77381|left popliteal artery|Artère poplitée (gauche)|lower|arteries_lower|vascular_artery|FJ2086
FMA50641|postcommunicating part of right posterior cerebral artery|Segment post-communicant de l’artère cérébrale postérieure (droite)|skull|arteries_head|vascular_artery|FJ1714
FMA50642|postcommunicating part of left posterior cerebral artery|Segment post-communicant de l’artère cérébrale postérieure (gauche)|skull|arteries_head|vascular_artery|FJ1714M
FMA14817|posterior cecal artery|Artère cæcale postérieure|abdomen|arteries_trunk|vascular_artery|FJ3553
FMA22685|right posterior circumflex humeral artery|Artère circonflexe humérale postérieure (droite)|upper|arteries_upper|vascular_artery|FJ2291,FJ2292
FMA22687|left posterior circumflex humeral artery|Artère circonflexe humérale postérieure (gauche)|upper|arteries_upper|vascular_artery|FJ2239,FJ2240
FMA50085|right posterior communicating artery|Artère communicante postérieure (droite)|skull|arteries_head|vascular_artery|FJ1713
FMA50086|left posterior communicating artery|Artère communicante postérieure (gauche)|skull|arteries_head|vascular_artery|FJ1713M
FMA70489|posterior division of right renal artery|Division postérieure de l’artère rénale (droite)|abdomen|arteries_trunk|vascular_artery|FJ3573,FJ3574,FJ3575
FMA70490|posterior division of left renal artery|Division postérieure de l’artère rénale (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3473,FJ3474,FJ3475
FMA50519|right posterior inferior cerebellar artery|Artère cérébelleuse postéro-inférieure (droite)|skull|arteries_head|vascular_artery|FJ1700,FJ1701,FJ1702,FJ1703,FJ1704,FJ1705,FJ1706,FJ1707,FJ1708,FJ1709,FJ1710,FJ1711,FJ1715
FMA50520|left posterior inferior cerebellar artery|Artère cérébelleuse postéro-inférieure (gauche)|skull|arteries_head|vascular_artery|FJ1700M,FJ1701M,FJ1702M,FJ1703M,FJ1704M,FJ1705M,FJ1706M,FJ1707M,FJ1708M,FJ1709M,FJ1710M,FJ1711M,FJ1715M
FMA70480|posterior inferior pancreaticoduodenal artery|Artère pancréatico-duodénale postéro-inférieure|abdomen|arteries_trunk|vascular_artery|FJ3546
FMA86348|posterior segmental branch of right renal artery|Rameau segmentaire postérieur de l’artère rénale (droite)|abdomen|arteries_trunk|vascular_artery|FJ2045
FMA86349|posterior segmental branch of left renal artery|Rameau segmentaire postérieur de l’artère rénale (gauche)|abdomen|arteries_trunk|vascular_artery|FJ2053,FJ2054
FMA14784|posterior superior pancreaticoduodenal artery|Artère pancréatico-duodénale postéro-supérieure|abdomen|arteries_trunk|vascular_artery|FJ3557
FMA43898|right posterior tibial artery|Artère tibiale postérieure (droite)|lower|arteries_lower|vascular_artery|FJ2172
FMA43899|left posterior tibial artery|Artère tibiale postérieure (gauche)|lower|arteries_lower|vascular_artery|FJ2087
FMA4712|posterior vein of left ventricle|Veine postérieure du ventricule (gauche)|thorax|coronary|vascular_coronary_vein|FJ2701,FJ2702,FJ2706,FJ2707,FJ2708,FJ2709,FJ2710,FJ2711,FJ2712,FJ2713
FMA50660|posteromedial central branch of right posterior cerebral artery|Rameau central postéro-médial de l’artère cérébrale postérieure (droite)|skull|arteries_head|vascular_artery|FJ1721
FMA50661|posteromedial central branch of left posterior cerebral artery|Rameau central postéro-médial de l’artère cérébrale postérieure (gauche)|skull|arteries_head|vascular_artery|FJ1721M
FMA71904|pre-hepatic portal vein|Segment préhépatique de la veine porte|abdomen|veins_trunk|vascular_portal|FJ3082
FMA50639|precommunicating part of right posterior cerebral artery|Segment précommunicant de l’artère cérébrale postérieure (droite)|skull|arteries_head|vascular_artery|FJ1723
FMA50640|precommunicating part of left posterior cerebral artery|Segment précommunicant de l’artère cérébrale postérieure (gauche)|skull|arteries_head|vascular_artery|FJ1723M
FMA8612|pulmonary trunk|Tronc pulmonaire|thorax|pulmonary|vascular_pulmonary_artery|FJ2966
FMA22733|right radial artery|Artère radiale (droite)|upper|arteries_upper|vascular_artery|FJ2294
FMA22734|left radial artery|Artère radiale (gauche)|upper|arteries_upper|vascular_artery|FJ2242
FMA14335|right renal vein|Veine rénale (droite)|abdomen|veins_trunk|vascular_vein|FJ3577,FJ3578
FMA14336|left renal vein|Veine rénale (gauche)|abdomen|veins_trunk|vascular_vein|FJ3477,FJ3478
FMA4112|left second posterior intercostal artery|Deuxième artère intercostale postérieure (gauche)|thorax|arteries_trunk|vascular_artery|FJ1974
FMA5041|right second posterior intercostal artery|Deuxième artère intercostale postérieure (droite)|thorax|arteries_trunk|vascular_artery|FJ1950
FMA71562|set of calcaneal branches of posterior tibial artery|Rameaux calcanéens de l’artère tibiale postérieure|feet|arteries_lower|vascular_artery|FJ2091,FJ2195
FMA71537|set of oesophageal branches of thoracic aorta|Rameaux œsophagiens de l’aorte thoracique|thorax|arteries_trunk|vascular_artery|FJ3431
FMA71502|set of posterior temporal branches of lateral occipital artery|Rameaux temporaux postérieurs de l’artère occipitale latérale|skull|arteries_head|vascular_artery|FJ1719,FJ1719M
FMA14830|sigmoid artery|Artère sigmoïdienne|abdomen|arteries_trunk|vascular_artery|FJ3638
FMA15395|sigmoid vein|Veine sigmoïdienne|abdomen|veins_trunk|vascular_vein|FJ3639
FMA4714|small cardiac vein|Petite veine du cœur|thorax|coronary|vascular_coronary_vein|FJ2724,FJ2731
FMA50366|sphenoid part of right middle cerebral artery|Segment sphénoïdal de l’artère cérébrale moyenne (droite)|skull|arteries_head|vascular_artery|FJ1692
FMA50367|sphenoid part of left middle cerebral artery|Segment sphénoïdal de l’artère cérébrale moyenne (gauche)|skull|arteries_head|vascular_artery|FJ1692M
FMA14773|splenic artery|Artère splénique|abdomen|arteries_trunk|vascular_artery|FJ2562,FJ3420,FJ3544,FJ3640
FMA14331|splenic vein|Veine splénique|abdomen|veins_trunk|vascular_vein|FJ3641
FMA3953|right subclavian artery|Artère subclavière (droite)|upper|arteries_upper|vascular_artery|FJ3579
FMA4694|left subclavian artery|Artère subclavière (gauche)|upper|arteries_upper|vascular_artery|FJ3479
FMA4755|right subclavian vein|Veine subclavière (droite)|upper|veins_upper|vascular_vein|FJ3587
FMA4763|left subclavian vein|Veine subclavière (gauche)|upper|veins_upper|vascular_vein|FJ3486
FMA4634|right subcostal artery|Artère subcostale (droite)|thorax|arteries_trunk|vascular_artery|FJ1967
FMA4654|left subcostal artery|Artère subcostale (gauche)|thorax|arteries_trunk|vascular_artery|FJ1977
FMA4844|right subcostal vein|Veine subcostale (droite)|thorax|veins_trunk|vascular_vein|FJ1995
FMA4951|left subcostal vein|Veine subcostale (gauche)|thorax|veins_trunk|vascular_vein|FJ1987
FMA22678|right subscapular artery|Artère subscapulaire (droite)|upper|arteries_upper|vascular_artery|FJ2298
FMA22679|left subscapular artery|Artère subscapulaire (gauche)|upper|arteries_upper|vascular_artery|FJ2246
FMA23114|right subscapular vein|Veine subscapulaire (droite)|upper|veins_upper|vascular_vein|FJ2299
FMA23115|left subscapular vein|Veine subscapulaire (gauche)|upper|veins_upper|vascular_vein|FJ2247
FMA10683|left superficial cervical artery|Artère cervicale superficielle (gauche)|neck|arteries_head|vascular_artery|FJ2256
FMA10700|right superficial cervical artery|Artère cervicale superficielle (droite)|neck|arteries_head|vascular_artery|FJ2308
FMA21384|superficial dorsal vein of penis|Veine dorsale superficielle du pénis|pelvis|veins_trunk|vascular_vein|FJ2208
FMA21385|right superficial dorsal vein of penis|Veine dorsale superficielle du pénis (droite)|pelvis|veins_trunk|vascular_vein|FJ3637
FMA21386|left superficial dorsal vein of penis|Veine dorsale superficielle du pénis (gauche)|pelvis|veins_trunk|vascular_vein|FJ3426
FMA20735|right superficial epigastric artery|Artère épigastrique superficielle (droite)|abdomen|arteries_trunk|vascular_artery|FJ3614
FMA20736|left superficial epigastric artery|Artère épigastrique superficielle (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3529
BP3D_right_epigastric_venous_portion|right epigastric venous portion|Portion veineuse épigastrique droite · identification précise incertaine|abdomen|veins_trunk|vascular_uncertain_epigastric|FJ2178
BP3D_left_epigastric_venous_portion|left epigastric venous portion|Portion veineuse épigastrique gauche · identification précise incertaine|abdomen|veins_trunk|vascular_uncertain_epigastric|FJ2122
FMA43937|right superficial medial plantar artery|Artère plantaire médiale superficielle (droite)|feet|arteries_lower|vascular_artery|FJ2179
FMA43938|left superficial medial plantar artery|Artère plantaire médiale superficielle (gauche)|feet|arteries_lower|vascular_artery|FJ2089
FMA22915|right superficial palmar venous arch|Arcade veineuse palmaire superficielle (droite)|hands|veins_upper|vascular_vein|FJ2301
FMA22916|left superficial palmar venous arch|Arcade veineuse palmaire superficielle (gauche)|hands|veins_upper|vascular_vein|FJ2249
FMA50574|right superior cerebellar artery|Artère cérébelleuse supérieure (droite)|skull|arteries_head|vascular_artery|FJ1726
FMA50575|left superior cerebellar artery|Artère cérébelleuse supérieure (gauche)|skull|arteries_head|vascular_artery|FJ1726M
FMA3988|right superior epigastric artery|Artère épigastrique supérieure (droite)|abdomen|arteries_trunk|vascular_artery|FJ1936
FMA4083|left superior epigastric artery|Artère épigastrique supérieure (gauche)|abdomen|arteries_trunk|vascular_artery|FJ1971
FMA18909|right superior gluteal vein|Veine glutéale supérieure (droite)|pelvis|veins_trunk|vascular_vein|FJ3616
FMA18910|left superior gluteal vein|Veine glutéale supérieure (gauche)|pelvis|veins_trunk|vascular_vein|FJ3531
FMA4088|left superior intercostal artery|Artère intercostale suprême (gauche)|thorax|arteries_trunk|vascular_artery|FJ1970
FMA5042|right superior intercostal artery|Artère intercostale suprême (droite)|thorax|arteries_trunk|vascular_artery|FJ1935
FMA4797|left superior intercostal vein|Veine intercostale supérieure (gauche)|thorax|veins_trunk|vascular_vein|FJ1986
FMA4877|right superior intercostal vein|Veine intercostale supérieure (droite)|thorax|veins_trunk|vascular_vein|FJ1991
FMA14332|superior mesenteric vein|Veine mésentérique supérieure|abdomen|veins_trunk|vascular_vein|FJ3647
FMA78121|superior phrenic vein|Veine phrénique supérieure|thorax|veins_trunk|vascular_vein|FJ3648,FJ3649,FJ3650,FJ3651,FJ3652,FJ3653,FJ3654
FMA14832|superior rectal artery|Artère rectale supérieure|pelvis|arteries_trunk|vascular_artery|FJ3656
FMA15393|superior rectal vein|Veine rectale supérieure|pelvis|veins_trunk|vascular_vein|FJ3657
FMA86340|superior segmental branch of right renal artery|Rameau segmentaire supérieur de l’artère rénale (droite)|abdomen|arteries_trunk|vascular_artery|FJ2042
FMA86341|superior segmental branch of left renal artery|Rameau segmentaire supérieur de l’artère rénale (gauche)|abdomen|arteries_trunk|vascular_artery|FJ2052
FMA4720|superior vena cava|Veine cave supérieure|thorax|veins_trunk|vascular_vein|FJ3645
FMA14343|right suprarenal vein|Veine surrénale (droite)|abdomen|veins_trunk|vascular_vein|FJ3580
FMA14349|left suprarenal vein|Veine surrénale (gauche)|abdomen|veins_trunk|vascular_vein|FJ3480
FMA10681|left suprascapular artery|Artère suprascapulaire (gauche)|upper|arteries_upper|vascular_artery|FJ2251
FMA10698|right suprascapular artery|Artère suprascapulaire (droite)|upper|arteries_upper|vascular_artery|FJ2303
FMA50859|right suprascapular vein|Veine suprascapulaire (droite)|upper|veins_upper|vascular_vein|FJ2302
FMA50860|left suprascapular vein|Veine suprascapulaire (gauche)|upper|veins_upper|vascular_vein|FJ2250
FMA14759|right testicular artery|Artère testiculaire (droite)|pelvis|arteries_trunk|vascular_artery|FJ3617
FMA14760|left testicular artery|Artère testiculaire (gauche)|pelvis|arteries_trunk|vascular_artery|FJ3532
FMA14341|right testicular vein|Veine testiculaire (droite)|pelvis|veins_trunk|vascular_vein|FJ3618
FMA14345|left testicular vein|Veine testiculaire (gauche)|pelvis|veins_trunk|vascular_vein|FJ3533
FMA66321|right thoracodorsal artery|Artère thoraco-dorsale (droite)|upper|arteries_upper|vascular_artery|FJ2305
FMA66322|left thoracodorsal artery|Artère thoraco-dorsale (gauche)|upper|arteries_upper|vascular_artery|FJ2253
FMA71214|right thoracodorsal vein|Veine thoraco-dorsale (droite)|upper|veins_upper|vascular_vein|FJ2306
FMA71215|left thoracodorsal vein|Veine thoraco-dorsale (gauche)|upper|veins_upper|vascular_vein|FJ2254
FMA3992|right thyrocervical trunk|Tronc thyro-cervical (droite)|neck|arteries_head|vascular_artery|FJ2307
FMA4084|left thyrocervical trunk|Tronc thyro-cervical (gauche)|neck|arteries_head|vascular_artery|FJ2255
FMA10682|left transverse cervical artery|Artère transverse du cou (gauche)|neck|arteries_head|vascular_artery|FJ2257
FMA10699|right transverse cervical artery|Artère transverse du cou (droite)|neck|arteries_head|vascular_artery|FJ2309
FMA76574|trunk of gastroduodenal artery|Tronc de l’artère gastro-duodénale|abdomen|arteries_trunk|vascular_artery|FJ3432
FMA76128|trunk of inferior terminal branch of right middle cerebral artery|Tronc du rameau terminal inférieur de l’artère cérébrale moyenne (droite)|skull|arteries_head|vascular_artery|FJ1718
FMA76129|trunk of inferior terminal branch of left middle cerebral artery|Tronc du rameau terminal inférieur de l’artère cérébrale moyenne (gauche)|skull|arteries_head|vascular_artery|FJ1718M
FMA66363|trunk of right renal artery|Tronc de l’artère rénale (droite)|abdomen|arteries_trunk|vascular_artery|FJ3576
FMA66364|trunk of left renal artery|Tronc de l’artère rénale (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3476
FMA66358|trunk of superior mesenteric artery|Tronc de l’artère mésentérique supérieure|abdomen|arteries_trunk|vascular_artery|FJ3644
FMA66563|trunk of right thoraco-acromial artery|Tronc de l’artère thoraco-acromiale (droite)|upper|arteries_upper|vascular_artery|FJ2304
FMA66564|trunk of left thoraco-acromial artery|Tronc de l’artère thoraco-acromiale (gauche)|upper|arteries_upper|vascular_artery|FJ2252
FMA22797|right ulnar artery|Artère ulnaire (droite)|upper|arteries_upper|vascular_artery|FJ2310
FMA22798|left ulnar artery|Artère ulnaire (gauche)|upper|arteries_upper|vascular_artery|FJ2258
FMA70492|ureteric segment of right renal artery|Segment urétérique de l’artère rénale (droite)|abdomen|arteries_trunk|vascular_artery|FJ3581,FJ3582
FMA70493|ureteric segment of left renal artery|Segment urétérique de l’artère rénale (gauche)|abdomen|arteries_trunk|vascular_artery|FJ3481
FMA3714|variant artery|Artère bronchique variante|thorax|arteries_trunk|vascular_artery|FJ3418
FMA3958|right vertebral artery|Artère vertébrale (droite)|neck|arteries_head|vascular_artery|FJ1725
FMA4066|left vertebral artery|Artère vertébrale (gauche)|neck|arteries_head|vascular_artery|FJ1725M
"""

_ROWS = [row.split('|') for row in _DATA.strip().splitlines()]
_BY_NAME = {row[1]: row for row in _ROWS}

groups = {
    'coronary': ('Coronary circulation', 'Circulation coronaire'),
    'pulmonary': ('Pulmonary circulation', 'Circulation pulmonaire'),
    'arteries_head': ('Arteries · head and neck', 'Artères · tête et cou'),
    'veins_head': ('Veins · head and neck', 'Veines · tête et cou'),
    'arteries_trunk': ('Arteries · trunk and pelvis', 'Artères · tronc et pelvis'),
    'veins_trunk': ('Veins · trunk and pelvis', 'Veines · tronc et pelvis'),
    'arteries_upper': ('Arteries · upper limbs', 'Artères · membres supérieurs'),
    'veins_upper': ('Veins · upper limbs', 'Veines · membres supérieurs'),
    'arteries_lower': ('Arteries · lower limbs', 'Artères · membres inférieurs'),
    'veins_lower': ('Veins · lower limbs', 'Veines · membres inférieurs'),
}

# Original short descriptions. The model limitations describe the actual import,
# not an assertion that each selected source collection exhausts its vascular bed.
_COMMON_EN = 'The source surface shows the vessel course, without microscopic wall layers, capillary beds or simulated blood flow.'
_COMMON_FR = 'La surface source montre le trajet vasculaire, sans couches microscopiques de la paroi, lits capillaires ni circulation sanguine simulée.'
summaries = {
    'vascular_artery': (
        'Arterial vessel or named branch group carrying blood away from the heart toward its supply territory. ' + _COMMON_EN,
        'Vaisseau artériel ou groupe de branches nommé acheminant le sang depuis le cœur vers son territoire. ' + _COMMON_FR),
    'vascular_vein': (
        'Venous vessel or named tributary group contributing to blood return toward the heart. Venous valves are not separately modelled. ' + _COMMON_EN,
        'Vaisseau veineux ou groupe d’affluents nommé participant au retour du sang vers le cœur. Les valvules veineuses ne sont pas individualisées. ' + _COMMON_FR),
    'vascular_pulmonary_artery': (
        'Carries oxygen-poor blood from the right ventricle toward the lungs. Blue is an illustrative convention. The source branching does not resolve pulmonary capillaries.',
        'Achemine le sang pauvre en oxygène depuis le ventricule droit vers les poumons. Le bleu est une convention illustrative. Les ramifications sources ne représentent pas les capillaires pulmonaires.'),
    'vascular_pulmonary_vein': (
        'Returns oxygen-rich blood from the lungs to the left atrium. Red is an illustrative convention. Selected tributaries are grouped with their principal source vein.',
        'Ramène le sang riche en oxygène des poumons vers l’atrium gauche. Le rouge est une convention illustrative. Les affluents sélectionnés sont regroupés avec leur veine principale source.'),
    'vascular_coronary_artery': (
        'Part of the arterial supply to the heart muscle. The principal right and left coronary trees group their available source branches; additional branches remain separately selectable.',
        'Partie de l’apport artériel au muscle cardiaque. Les arbres coronaires principaux droit et gauche regroupent leurs branches disponibles ; des rameaux complémentaires restent sélectionnables séparément.'),
    'vascular_coronary_vein': (
        'Vessel draining blood from the heart muscle. Most cardiac venous return reaches the right atrium through the coronary sinus; anterior cardiac veins can drain directly to the atrium.',
        'Vaisseau drainant le sang du muscle cardiaque. La majeure partie du retour veineux cardiaque rejoint l’atrium droit par le sinus coronaire ; les veines cardiaques antérieures peuvent rejoindre directement l’atrium.'),
    'vascular_aorta': (
        'Main artery leaving the left ventricle. Its ascending portion, arch and descending portion are combined here. An overlapping alternate descending surface is excluded.',
        'Artère principale issue du ventricule gauche. Ses portions ascendante, arquée et descendante sont réunies ici. Une autre représentation descendante superposée est exclue.'),
    'vascular_portal': (
        'Part of the venous pathway bringing blood from digestive organs toward the liver before it returns to the heart. ' + _COMMON_EN,
        'Partie de la voie veineuse conduisant le sang des organes digestifs vers le foie avant son retour au cœur. ' + _COMMON_FR),
}

summaries['vascular_uncertain_epigastric'] = (
    'Venous surface in the epigastric region. Two nearly identical source variants call this trajectory superficial or superior epigastric vein. Only one surface is retained, with a local identifier; its precise branch identity remains unresolved.',
    'Surface veineuse de la région épigastrique. Deux variantes sources presque identiques nomment ce trajet veine épigastrique superficielle ou supérieure. Une seule surface est conservée sous un identifiant local ; son identité de branche précise reste indéterminée.')

references = [
    'https://dbarchive.biosciencedbc.jp/en/bodyparts3d/desc.html',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/20-1-structure-and-function-of-blood-vessels',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/20-5-circulatory-pathways',
    'https://openstax.org/books/anatomy-and-physiology-2e/pages/19-1-heart-anatomy',
]


def entries(concepts, partof):
    known = {row['element file id'] for rows in concepts.values() for row in rows}
    result, used = [], set()
    for identity, name, _, _, category, family, source_files in _ROWS:
        files = source_files.split(',')
        assert set(files) <= known, (identity, 'missing vascular OBJ identity')
        assert not used.intersection(files), (identity, 'overlapping vascular selection')
        assert category in groups and family in summaries
        used.update(files)
        result.append((identity, name, files, False, 0))
    assert len(result) == 396 and len(used) == 1014
    return result


def describe(name):
    _, _, french, region, _, family, _ = _BY_NAME[name]
    wiki = {
        'vascular_artery': ('Artery', 'Artère'),
        'vascular_vein': ('Vein', 'Veine'),
        'vascular_pulmonary_artery': ('Pulmonary artery', 'Artère pulmonaire'),
        'vascular_pulmonary_vein': ('Pulmonary vein', 'Veine pulmonaire'),
        'vascular_coronary_artery': ('Coronary circulation', 'Circulation coronaire'),
        'vascular_coronary_vein': ('Coronary circulation', 'Circulation coronaire'),
        'vascular_aorta': ('Aorta', 'Aorte'),
        'vascular_portal': ('Portal vein', 'Veine porte'),
        'vascular_uncertain_epigastric': ('Epigastric veins', 'Veine épigastrique'),
    }
    # Search the named vessel instead of opening a generic artery/vein search
    # for independently selectable vascular structures.
    wiki_en = name if not name.startswith(('branch of ', 'set of ', 'variant ')) else wiki[family][0]
    if family == 'vascular_uncertain_epigastric':
        return name[0].upper() + name[1:], french, family, region, *wiki[family]
    wiki_fr = french.replace(' et ses branches', '').replace(' et ses affluents', '').replace(' et ses ramifications', '')
    return name[0].upper() + name[1:], french, family, region, wiki_en, wiki_fr


def attributes(name):
    row = _BY_NAME[name]
    # Colour follows oxygenation convention, including the pulmonary exception.
    blue = row[5] in ('vascular_vein', 'vascular_coronary_vein', 'vascular_portal',
                      'vascular_pulmonary_artery', 'vascular_uncertain_epigastric')
    return {'category': row[4], 'color': [.24, .39, .76] if blue else [.80, .16, .19]}


def provenance(source, selected):
    return {
        'source': 'BodyParts3D 4.0',
        'license': 'CC BY 4.0',
        'structures': len(selected),
        'sourceElements': sum(len(files) for _, _, files, _, _ in selected),
        'trianglesBeforeConversion': 1893540,
        'isaMetadataSha256': hashlib.sha256((source / 'elements.tsv').read_bytes()).hexdigest(),
        'partOfMetadataSha256': hashlib.sha256((source / 'partof_elements.tsv').read_bytes()).hexdigest(),
        'modifications': 'Original coordinates and full mesh detail; OBJ to GLB, smooth normals and conventional red/blue colours. Named source collections group smaller branches. Each retained OBJ occurs in exactly one vascular selection. No vascular tube is fabricated or widened.',
        'sourcePartition': 'Explicit checked-in table includes vessels, trunks, segments and sets from IS-A plus selected PART-OF coronary, pulmonary, hepatic arterial and portal trees. Parent collections are partitioned after these named trees to avoid double rendering; their complementary branches remain separate. Mixed digital/perforating sets are split between hands and feet by the original geometry.',
        'excludedRedundantElements': {
            'FJ1846': 'Rounded duplicate of celiac trunk FJ3421, maximum vertex difference 0.01005 mm; retain the newer named representation.',
            'FJ1853': 'Rounded duplicate of portal segment FJ3082, maximum surface difference 0.01004 mm.',
            'FJ2025': 'Rounded duplicate of marginal colic artery FJ3534, maximum surface difference 0.01000 mm.',
            'FJ2011,FJ1928': 'Overlapping variants of the superior mesenteric artery trunk. Retain the more detailed named trunk FJ3644; complete trajectories overlap, maximum discrepancy 3.52 mm near endpoints.',
            'FJ1844': 'Overlapping alternate basilar artery; retain FJ1672. Reciprocal surface medians 0.227/0.436 mm.',
            'FJ2034': 'Overlapping ileal arterial segment; retain FJ3437. Reciprocal surface medians 0.245/0.294 mm.',
            'FJ2038,FJ2046': 'Older overlapping right/left renal artery trunks; retain FJ3576/FJ3476. Reciprocal surface medians 0.425/0.384 mm right and 0.198/0.097 mm left.',
            'FJ3530': 'Overlaps left epigastric venous surface FJ2122 with contradictory superior/superficial labels; keep FJ2122 under a local uncertain identity. Median reciprocal errors 0.009/0.040 mm; the older surface has a 7 mm endpoint extension.',
            'FJ3615': 'Overlaps FJ2178 but is labelled superior instead of superficial epigastric vein. Retain FJ2178 under a local, explicitly uncertain epigastric venous identity. Maximum reciprocal surface error 0.179 mm.',
            'FJ1931,FJ1932': 'Alternate thoracic/abdominal descending aorta surfaces overlapping the complete descending aorta FJ3427. Preserve FJ3427 together with arch FJ3411 and ascending aorta FJ3413.',
            'FJ2013': 'Identical vertex coordinates to FJ1846 (celiac artery).',
            'FJ2386': 'Identical vertex coordinates to FJ1916 (caudate branch of right hepatic artery).',
            'FJ2394': 'Identical vertex coordinates to FJ1924 (right-lobe branch of right hepatic artery).',
        },
        'laterality': 'Ascending lumbar vein source association corrected: FMA4843 right uses FJ3493 (entirely anatomical right and joining the azygos pathway), FMA4950 left uses FJ3589 (entirely left and joining the hemiazygos pathway). Source metadata associates these files in reverse. Coordinates and shapes are unchanged. Laterality of visceral vessels refers to named branches and does not necessarily follow the body midline.',
        'limitations': 'Macroscopic source subset, not a complete vascular system: no capillary beds, no separate vessel-wall layers or valves, no haemodynamics. Some fine branches are grouped and remain incomplete in the source. Pulmonary vessels originate from BodyParts3D and lung surfaces from a registered Z-Anatomy model, so local contacts remain approximate. BodyParts3D 4.0 metadata contains no separately identifiable lymph nodes or lymphatic vessels; those are not silently represented by blood vessels.',
        'references': references,
    }
