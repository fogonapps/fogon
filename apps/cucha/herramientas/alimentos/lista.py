"""
La base de alimentos de La Cucha: nombre de aquí (español e inglés), el alimento de USDA FoodData Central
del que salen los valores (por su número FDC) y porciones caseras en gramos.

Los valores por 100 g (calorías, proteína, carbohidratos, grasa, fibra) NO se escriben aquí: los saca
generar.py de los archivos oficiales de USDA (CC0). Las porciones caseras son aproximadas (pesos típicos);
en la app siempre se pueden poner los gramos exactos.

aprox=True: no hay ese alimento en USDA y se usa el más parecido (la app lo dice).
"""

# Porciones que se repiten
TAZA, MEDIA = ("Taza", "Cup"), ("Media taza", "Half cup")
CUCHARADA, CUCHARADITA = ("Cucharada", "Tablespoon"), ("Cucharadita", "Teaspoon")
UNIDAD, PORCION = ("Unidad", "Piece"), ("Porción", "Serving")
VASO, POCILLO, TAJADA = ("Vaso", "Glass"), ("Pocillo", "Mug"), ("Tajada", "Slice")
PRESA, FILETE, PUNADO = ("Presa", "Piece"), ("Filete", "Fillet"), ("Puñado", "Handful")

def a(id, es, en, grupo, fdc, *porciones, aprox=False):
    return dict(id=id, es=es, en=en, grupo=grupo, fdc=fdc, porciones=[(p[0], p[1], g) for p, g in porciones], aprox=aprox)

ALIMENTOS = [
    # Granos, harinas y panes
    a("arroz_blanco", "Arroz blanco cocido", "White rice, cooked", "granos", 168878, (TAZA, 158), (MEDIA, 79), (("Cucharón", "Ladle"), 100)),
    a("arroz_integral", "Arroz integral cocido", "Brown rice, cooked", "granos", 169704, (TAZA, 195), (MEDIA, 98)),
    a("avena", "Avena en hojuelas (cruda)", "Rolled oats (dry)", "granos", 173904, (CUCHARADA, 5), (MEDIA, 40), (TAZA, 81)),
    a("avena_agua", "Avena cocida en agua", "Oatmeal made with water", "granos", 2708381, (TAZA, 234), (POCILLO, 240)),
    a("arepa", "Arepa de maíz", "Corn arepa", "granos", 168070, (("Arepa mediana", "Medium arepa"), 80), (("Arepa grande", "Large arepa"), 120), (("Arepa pequeña", "Small arepa"), 45)),
    a("harina_maiz", "Harina de maíz precocida", "Precooked corn flour", "granos", 169694, (TAZA, 114), (CUCHARADA, 9)),
    a("mazorca", "Mazorca (maíz tierno cocido)", "Sweet corn, cooked", "granos", 169999, ((("Mazorca", "Ear of corn")), 100), (MEDIA, 82)),
    a("mote", "Maíz pelado (mote)", "Hominy", "granos", 169701, (TAZA, 165), (MEDIA, 82)),
    a("pan_blanco", "Pan blanco", "White bread", "granos", 174924, (TAJADA, 25), (UNIDAD, 50)),
    a("pan_integral", "Pan integral", "Whole-wheat bread", "granos", 172688, (TAJADA, 28)),
    a("pan_aliñado", "Pan de sal (pan pequeño)", "Dinner roll", "granos", 172793, (UNIDAD, 28)),
    a("croissant", "Croissant (cruasán)", "Croissant", "granos", 174987, (UNIDAD, 57)),
    a("pasta", "Pasta cocida", "Pasta, cooked", "granos", 169737, (TAZA, 140), (MEDIA, 70)),
    a("tortilla_maiz", "Tortilla de maíz", "Corn tortilla", "granos", 175036, (UNIDAD, 26)),
    a("tortilla_trigo", "Tortilla de trigo", "Flour tortilla", "granos", 175037, (UNIDAD, 45)),
    a("quinua", "Quinua cocida", "Quinoa, cooked", "granos", 168917, (TAZA, 185), (MEDIA, 92)),
    a("galletas_soda", "Galletas de soda", "Saltine crackers", "granos", 172746, (UNIDAD, 3), (("Paquete", "Pack"), 30)),
    a("cereal_hojuelas", "Hojuelas de maíz (cereal)", "Corn flakes", "granos", 2708453, (TAZA, 28)),
    a("granola", "Granola", "Granola", "granos", 171646, (MEDIA, 61), (CUCHARADA, 8)),
    a("crispetas", "Crispetas (sin mantequilla)", "Air-popped popcorn", "granos", 167959, (TAZA, 8)),

    # Tubérculos y plátanos
    a("papa", "Papa cocida", "Potato, boiled", "tuberculos", 170440, (("Papa mediana", "Medium potato"), 150), (TAZA, 156)),
    a("papa_horno", "Papa al horno con cáscara", "Baked potato with skin", "tuberculos", 170093, (("Papa mediana", "Medium potato"), 170)),
    a("pure_papa", "Puré de papa", "Mashed potatoes", "tuberculos", 170493, (TAZA, 210), (MEDIA, 105)),
    a("papas_fritas", "Papas a la francesa", "French fries", "tuberculos", 170698, (("Porción mediana", "Medium serving"), 117), (("Porción pequeña", "Small serving"), 71)),
    a("yuca", "Yuca cocida", "Cassava, cooked", "tuberculos", 2709564, (("Trozo", "Piece"), 100), (TAZA, 137)),
    a("yuca_frita", "Yuca frita", "Cassava fries", "tuberculos", 2709565, (PORCION, 100)),
    a("platano_verde", "Plátano verde cocido", "Green plantain, boiled", "tuberculos", 168216, (("Medio plátano", "Half plantain"), 90), (TAZA, 154)),
    a("platano_maduro", "Plátano maduro cocido", "Ripe plantain, cooked", "tuberculos", 2709558, (("Medio plátano", "Half plantain"), 90), (TAJADA, 30)),
    a("patacon", "Patacón (plátano verde frito)", "Fried green plantain (patacón)", "tuberculos", 168199, (UNIDAD, 40)),
    a("tajadas", "Tajadas de maduro fritas", "Fried ripe plantain slices", "tuberculos", 168200, (TAJADA, 30), (PORCION, 90)),
    a("batata", "Batata (camote) cocida", "Sweet potato, boiled", "tuberculos", 168484, (UNIDAD, 150), (TAZA, 200)),
    a("name", "Ñame cocido", "Yam, cooked", "tuberculos", 170551, (TAZA, 136)),
    a("malanga", "Bore o malanga cocida", "Taro, cooked", "tuberculos", 168486, (TAZA, 132)),

    # Leguminosas
    a("frijol_rojo", "Fríjoles rojos cocidos", "Red kidney beans, cooked", "legumbres", 175194, (TAZA, 177), (MEDIA, 89), (("Cucharón", "Ladle"), 120)),
    a("frijol_cargamanto", "Fríjol cargamanto cocido", "Cranberry-type beans, cooked", "legumbres", 175200, (TAZA, 171), (MEDIA, 86), aprox=True),
    a("frijol_negro", "Fríjoles negros cocidos", "Black beans, cooked", "legumbres", 173735, (TAZA, 172), (MEDIA, 86)),
    a("frijol_blanco", "Fríjol blanco cocido", "White beans, cooked", "legumbres", 173746, (TAZA, 182)),
    a("lentejas", "Lentejas cocidas", "Lentils, cooked", "legumbres", 172421, (TAZA, 198), (MEDIA, 99), (("Cucharón", "Ladle"), 120)),
    a("garbanzos", "Garbanzos cocidos", "Chickpeas, cooked", "legumbres", 173757, (TAZA, 164), (MEDIA, 82)),
    a("arveja_seca", "Arveja seca cocida", "Split peas, cooked", "legumbres", 172429, (TAZA, 196)),
    a("tofu", "Tofu firme", "Firm tofu", "legumbres", 172448, (PORCION, 85)),

    # Carnes
    a("pechuga", "Pechuga de pollo asada", "Chicken breast, roasted", "carnes", 171477, (("Filete", "Fillet"), 120), (("Media pechuga", "Half breast"), 140), (("100 g", "100 g"), 100)),
    a("muslo_pollo", "Muslo de pollo asado (sin piel)", "Chicken thigh, roasted (skinless)", "carnes", 172388, (PRESA, 70)),
    a("pierna_pollo", "Pierna de pollo sudada (sin piel)", "Chicken drumstick, stewed (skinless)", "carnes", 172377, (PRESA, 70)),
    a("pollo_con_piel", "Pollo asado con piel", "Roast chicken with skin", "carnes", 171450, (PRESA, 100), (("Cuarto de pollo", "Quarter chicken"), 200)),
    a("pollo_molido", "Pollo molido cocido", "Ground chicken, cooked", "carnes", 171117, (PORCION, 100)),
    a("higado_pollo", "Hígado de pollo", "Chicken liver", "carnes", 171061, (PORCION, 100)),
    a("carne_molida", "Carne molida de res (85/15)", "Ground beef (85/15), cooked", "carnes", 174032, (PORCION, 100), (("Hamburguesa", "Burger patty"), 90)),
    a("carne_molida_magra", "Carne molida magra (90/10)", "Lean ground beef (90/10), cooked", "carnes", 171794, (PORCION, 100)),
    a("carne_asada", "Carne de res asada (solomo)", "Grilled beef (sirloin)", "carnes", 174054, (("Filete", "Steak"), 150), (PORCION, 100)),
    a("carne_desmechada", "Carne desmechada (posta, sudada)", "Shredded braised beef", "carnes", 170215, (PORCION, 100), (MEDIA, 70)),
    a("sobrebarriga", "Sobrebarriga cocida", "Braised flank steak", "carnes", 168610, (PORCION, 120)),
    a("higado_res", "Hígado de res frito", "Beef liver, pan-fried", "carnes", 168627, (("Filete", "Fillet"), 100)),
    a("mondongo_carne", "Callo de res (mondongo) cocido", "Beef tripe, cooked", "carnes", 174769, (PORCION, 100)),
    a("cerdo", "Lomo de cerdo asado", "Pork loin, grilled", "carnes", 168240, (("Chuleta", "Chop"), 120), (PORCION, 100)),
    a("costilla_cerdo", "Costilla de cerdo", "Pork spareribs", "carnes", 167854, (PORCION, 100)),
    a("tocino", "Tocineta", "Bacon", "carnes", 167914, (TAJADA, 8)),
    a("chicharron", "Chicharrón", "Pork rinds (chicharrón)", "carnes", 167961, (PORCION, 30), aprox=True),
    a("chorizo", "Chorizo", "Chorizo sausage", "carnes", 2706179, (UNIDAD, 60)),
    a("morcilla", "Morcilla (rellena)", "Blood sausage", "carnes", 171618, (PORCION, 60)),
    a("salchicha", "Salchicha", "Frankfurter", "carnes", 174614, (UNIDAD, 45)),
    a("jamon", "Jamón", "Ham", "carnes", 173864, (TAJADA, 20)),
    a("pavo_tajado", "Pechuga de pavo tajada", "Sliced turkey breast", "carnes", 172941, (TAJADA, 20)),
    a("pavo_molido", "Pavo molido cocido", "Ground turkey, cooked", "carnes", 171506, (PORCION, 100)),

    # Pescados y mariscos
    a("tilapia", "Mojarra (tilapia) asada", "Tilapia, baked", "pescados", 2706319, (FILETE, 120), (UNIDAD, 200)),
    a("mojarra_frita", "Mojarra frita", "Fried tilapia", "pescados", 2706322, (UNIDAD, 200)),
    a("atun", "Atún en agua (escurrido)", "Tuna in water, drained", "pescados", 173709, (("Lata", "Can"), 120), (MEDIA, 77)),
    a("salmon", "Salmón", "Salmon", "pescados", 175168, (FILETE, 120)),
    a("sardinas", "Sardinas en aceite (escurridas)", "Sardines in oil, drained", "pescados", 175139, (("Lata", "Can"), 92), (UNIDAD, 24)),
    a("bagre", "Bagre", "Catfish", "pescados", 175166, (FILETE, 120), aprox=True),
    a("bacalao", "Pescado blanco (bacalao)", "White fish (cod)", "pescados", 171956, (FILETE, 120)),
    a("camarones", "Camarones cocidos", "Shrimp, cooked", "pescados", 175180, (PORCION, 85), (TAZA, 145)),

    # Huevos y lácteos
    a("huevo", "Huevo cocido", "Hard-boiled egg", "lacteos", 173424, (UNIDAD, 50)),
    a("huevo_revuelto", "Huevo revuelto", "Scrambled egg", "lacteos", 172187, (UNIDAD, 61)),
    a("huevo_crudo", "Huevo (crudo, para recetas)", "Egg (raw, for recipes)", "lacteos", 171287, (UNIDAD, 50)),
    a("clara", "Clara de huevo", "Egg white", "lacteos", 172183, (UNIDAD, 33)),
    a("pericos", "Huevos pericos", "Scrambled eggs with tomato and onion", "lacteos", 2707229, (("Dos huevos", "Two eggs"), 130)),
    a("leche_entera", "Leche entera", "Whole milk", "lacteos", 171265, (VASO, 244), (POCILLO, 200)),
    a("leche_semi", "Leche semidescremada", "2% milk", "lacteos", 171267, (VASO, 244), (POCILLO, 200)),
    a("leche_descremada", "Leche descremada", "Skim milk", "lacteos", 171269, (VASO, 245)),
    a("leche_deslactosada", "Leche deslactosada entera", "Lactose-free whole milk", "lacteos", 2705392, (VASO, 244), (POCILLO, 200)),
    a("leche_deslactosada_light", "Leche deslactosada descremada", "Lactose-free skim milk", "lacteos", 2705390, (VASO, 245)),
    a("yogur_griego", "Yogur griego natural sin grasa", "Plain nonfat Greek yogurt", "lacteos", 170894, (TAZA, 245), (("Vasito", "Cup"), 170)),
    a("yogur", "Yogur natural entero", "Plain whole-milk yogurt", "lacteos", 171284, (TAZA, 245), (("Vasito", "Cup"), 150)),
    a("kumis", "Kumis", "Kumis (fermented milk)", "lacteos", 2705394, (VASO, 240), aprox=True),
    a("kefir", "Kéfir", "Kefir", "lacteos", 2705394, (VASO, 240)),
    a("queso_campesino", "Queso campesino", "Fresh farmer's cheese", "lacteos", 172223, (TAJADA, 30)),
    a("cuajada", "Cuajada", "Fresh curd cheese", "lacteos", 172224, (TAJADA, 40), aprox=True),
    a("queso_mozzarella", "Queso mozzarella", "Mozzarella", "lacteos", 170845, (TAJADA, 20)),
    a("queso_amarillo", "Queso amarillo (cheddar)", "Cheddar cheese", "lacteos", 170899, (TAJADA, 20)),
    a("queso_cottage", "Queso cottage", "Cottage cheese", "lacteos", 172182, (MEDIA, 113)),
    a("queso_crema", "Queso crema", "Cream cheese", "lacteos", 2705760, (CUCHARADA, 15)),
    a("crema_leche", "Crema de leche", "Heavy cream", "lacteos", 2705597, (CUCHARADA, 15)),
    a("leche_condensada", "Leche condensada", "Sweetened condensed milk", "lacteos", 2705402, (CUCHARADA, 19)),
    a("arequipe", "Arequipe", "Dulce de leche", "lacteos", 173461, (CUCHARADA, 19)),
    a("helado", "Helado de vainilla", "Vanilla ice cream", "lacteos", 167575, (("Bola", "Scoop"), 66)),

    # Frutas
    a("banano", "Banano", "Banana", "frutas", 173944, (UNIDAD, 118), (("Banano pequeño", "Small banana"), 90)),
    a("manzana", "Manzana", "Apple", "frutas", 171688, (UNIDAD, 182)),
    a("naranja", "Naranja", "Orange", "frutas", 169097, (UNIDAD, 140)),
    a("mandarina", "Mandarina", "Tangerine", "frutas", 169105, (UNIDAD, 88)),
    a("papaya", "Papaya", "Papaya", "frutas", 169926, (TAZA, 145), (TAJADA, 150)),
    a("mango", "Mango", "Mango", "frutas", 169910, (UNIDAD, 200), (TAZA, 165)),
    a("pina", "Piña", "Pineapple", "frutas", 169124, (TAZA, 165), (TAJADA, 84)),
    a("guayaba", "Guayaba", "Guava", "frutas", 173044, (UNIDAD, 55)),
    a("maracuya", "Maracuyá", "Passion fruit", "frutas", 169108, (UNIDAD, 18), (("Pulpa de 3", "Pulp of 3"), 54)),
    a("granadilla", "Granadilla", "Sweet granadilla", "frutas", 169108, (UNIDAD, 50), aprox=True),
    a("lulo", "Lulo (pulpa)", "Lulo (pulp)", "frutas", 167790, (UNIDAD, 60), (TAZA, 140)),
    a("guanabana", "Guanábana", "Soursop", "frutas", 167761, (TAZA, 225)),
    a("feijoa", "Feijoa", "Feijoa", "frutas", 168176, (UNIDAD, 50)),
    a("mamey", "Mamey", "Mamey sapote", "frutas", 167760, (TAZA, 175)),
    a("aguacate", "Aguacate", "Avocado", "frutas", 171705, (("Medio aguacate", "Half avocado"), 100), (TAJADA, 25)),
    a("fresas", "Fresas", "Strawberries", "frutas", 167762, (TAZA, 152), (UNIDAD, 12)),
    a("moras", "Moras", "Blackberries", "frutas", 173946, (TAZA, 144)),
    a("sandia", "Patilla", "Watermelon", "frutas", 167765, (TAJADA, 280), (TAZA, 152)),
    a("melon", "Melón", "Cantaloupe", "frutas", 169092, (TAZA, 160)),
    a("uvas", "Uvas", "Grapes", "frutas", 174683, (TAZA, 151), (PUNADO, 50)),
    a("pera", "Pera", "Pear", "frutas", 169118, (UNIDAD, 178)),
    a("durazno", "Durazno", "Peach", "frutas", 169928, (UNIDAD, 150)),
    a("kiwi", "Kiwi", "Kiwi", "frutas", 168153, (UNIDAD, 69)),
    a("limon", "Limón", "Lime", "frutas", 168155, (UNIDAD, 67), (("Jugo de un limón", "Juice of one lime"), 44)),
    a("coco", "Coco (pulpa)", "Coconut meat", "frutas", 170169, (TAZA, 80)),
    a("uvas_pasas", "Uvas pasas", "Raisins", "frutas", 2709212, (CUCHARADA, 9), (PUNADO, 30)),
    a("datiles", "Dátiles", "Dates", "frutas", 171726, (UNIDAD, 7)),
    a("bocadillo", "Bocadillo de guayaba", "Guava paste", "frutas", 2710307, (UNIDAD, 30), (("Lonja", "Slab"), 50)),

    # Verduras
    a("tomate", "Tomate", "Tomato", "verduras", 170457, (UNIDAD, 123)),
    a("cebolla", "Cebolla cabezona", "Onion", "verduras", 170000, (UNIDAD, 110), (CUCHARADA, 10)),
    a("cebolla_larga", "Cebolla larga", "Scallion", "verduras", 170005, (("Tallo", "Stalk"), 15)),
    a("ajo", "Ajo", "Garlic", "verduras", 169230, (("Diente", "Clove"), 3)),
    a("zanahoria", "Zanahoria", "Carrot", "verduras", 170393, (UNIDAD, 61), (TAZA, 128)),
    a("lechuga", "Lechuga", "Lettuce", "verduras", 169249, (TAZA, 36)),
    a("repollo", "Repollo", "Cabbage", "verduras", 169975, (TAZA, 89)),
    a("brocoli", "Brócoli cocido", "Broccoli, cooked", "verduras", 169967, (TAZA, 156)),
    a("coliflor", "Coliflor", "Cauliflower", "verduras", 169986, (TAZA, 107)),
    a("espinaca", "Espinaca", "Spinach", "verduras", 168462, (TAZA, 30)),
    a("pepino", "Pepino cohombro", "Cucumber", "verduras", 168409, (UNIDAD, 300), (TAZA, 104)),
    a("pimenton", "Pimentón", "Red bell pepper", "verduras", 170108, (UNIDAD, 119)),
    a("arveja", "Arveja verde cocida", "Green peas, cooked", "verduras", 170420, (MEDIA, 80)),
    a("habichuela", "Habichuela cocida", "Green beans, cooked", "verduras", 2710803, (TAZA, 125)),
    a("remolacha", "Remolacha cocida", "Beets, cooked", "verduras", 169146, (UNIDAD, 50), (MEDIA, 85)),
    a("ahuyama", "Ahuyama cocida", "Squash, cooked", "verduras", 169296, (TAZA, 205)),
    a("champinones", "Champiñones", "Mushrooms", "verduras", 169251, (TAZA, 70)),
    a("berenjena", "Berenjena", "Eggplant", "verduras", 169228, (TAZA, 82)),
    a("apio", "Apio", "Celery", "verduras", 169988, (("Tallo", "Stalk"), 40)),
    a("cilantro", "Cilantro", "Cilantro", "verduras", 169997, (CUCHARADA, 1)),

    # Grasas, nueces y semillas
    a("aceite", "Aceite vegetal", "Vegetable oil", "grasas", 172336, (CUCHARADA, 14), (CUCHARADITA, 4.5)),
    a("aceite_oliva", "Aceite de oliva", "Olive oil", "grasas", 171413, (CUCHARADA, 13.5), (CUCHARADITA, 4.5)),
    a("mantequilla", "Mantequilla", "Butter", "grasas", 173410, (CUCHARADA, 14), (CUCHARADITA, 5)),
    a("mayonesa", "Mayonesa", "Mayonnaise", "grasas", 171009, (CUCHARADA, 14)),
    a("mani", "Maní tostado sin sal", "Dry-roasted peanuts", "grasas", 173806, (PUNADO, 28), (CUCHARADA, 9)),
    a("mantequilla_mani", "Mantequilla de maní", "Peanut butter", "grasas", 172470, (CUCHARADA, 16)),
    a("almendras", "Almendras", "Almonds", "grasas", 170567, (PUNADO, 28), (UNIDAD, 1.2)),
    a("nueces", "Nueces", "Walnuts", "grasas", 170187, (PUNADO, 28)),
    a("chia", "Chía", "Chia seeds", "grasas", 170554, (CUCHARADA, 12)),

    # Dulces y salsas
    a("azucar", "Azúcar", "Sugar", "dulces", 169655, (CUCHARADITA, 4), (CUCHARADA, 12.5)),
    a("panela", "Panela", "Panela (unrefined cane sugar)", "dulces", 168833, (("Cucharada rallada", "Grated tablespoon"), 12), (("Trozo", "Piece"), 30), aprox=True),
    a("miel", "Miel de abejas", "Honey", "dulces", 169640, (CUCHARADA, 21), (CUCHARADITA, 7)),
    a("chocolate_oscuro", "Chocolate oscuro 70%", "Dark chocolate 70%", "dulces", 170273, (("Cuadrito", "Square"), 10), (("Barra", "Bar"), 40)),
    a("cacao", "Cacao en polvo sin azúcar", "Unsweetened cocoa powder", "dulces", 169593, (CUCHARADA, 5)),
    a("galletas", "Galletas de mantequilla", "Butter cookies", "dulces", 174950, (UNIDAD, 5)),
    a("ponque", "Ponqué", "Pound cake", "dulces", 2707882, (TAJADA, 50)),
    a("bunuelo", "Buñuelo", "Buñuelo (fried cheese fritter)", "dulces", 168071, (UNIDAD, 40), aprox=True),
    a("gelatina", "Gelatina preparada", "Gelatin dessert", "dulces", 2710310, (MEDIA, 135)),
    a("salsa_tomate", "Salsa de tomate", "Ketchup", "dulces", 168556, (CUCHARADA, 17)),
    a("papas_paquete", "Papas de paquete", "Potato chips", "dulces", 169677, (("Paquete pequeño", "Small bag"), 28)),
    a("barra_granola", "Barra de cereal", "Granola bar", "dulces", 167542, (UNIDAD, 25)),

    # Comidas hechas (cuando no se cocina en casa)
    a("empanada", "Empanada de carne", "Beef empanada", "hechas", 2708711, (UNIDAD, 70), (("Empanada grande", "Large empanada"), 120)),
    a("empanada_pollo", "Empanada de pollo", "Chicken empanada", "hechas", 2708713, (UNIDAD, 70)),
    a("tamal", "Tamal", "Tamale", "hechas", 2708570, (UNIDAD, 300), aprox=True),
    a("sopa_pollo", "Sopa o caldo de pollo", "Chicken soup", "hechas", 2707134, (("Plato", "Bowl"), 300)),
    a("sopa_res_verduras", "Sopa de res con verduras", "Beef and vegetable soup", "hechas", 171190, (("Plato", "Bowl"), 300)),
    a("estofado_res", "Carne guisada con papa", "Beef stew", "hechas", 2706592, (("Plato", "Plate"), 250)),

    # Bebidas
    a("agua", "Agua", "Water", "bebidas", 2710707, (VASO, 250)),
    a("tinto", "Tinto (café negro)", "Black coffee", "bebidas", 2710375, (POCILLO, 120), (("Taza grande", "Large mug"), 240)),
    a("te", "Té o aromática", "Tea", "bebidas", 173227, (POCILLO, 240)),
    a("chocolate_caliente", "Chocolate en leche", "Hot chocolate", "bebidas", 2705472, (POCILLO, 240)),
    a("jugo_naranja", "Jugo de naranja natural", "Fresh orange juice", "bebidas", 169098, (VASO, 248)),
    a("jugo_mora", "Jugo de mora (sin azúcar)", "Blackberry juice (unsweetened)", "bebidas", 173947, (VASO, 250)),
    a("limonada", "Limonada", "Lemonade", "bebidas", 2710570, (VASO, 250)),
    a("gaseosa", "Gaseosa", "Soda", "bebidas", 2710541, (VASO, 250), (("Lata", "Can"), 355)),
    a("gaseosa_cero", "Gaseosa sin azúcar", "Diet soda", "bebidas", 2710542, (VASO, 250), (("Lata", "Can"), 355)),
    a("bebida_soya", "Bebida de soya sin azúcar", "Unsweetened soy milk", "bebidas", 2705405, (VASO, 243)),
    a("bebida_almendras", "Bebida de almendras", "Almond milk", "bebidas", 2705410, (VASO, 240)),
    a("cerveza", "Cerveza", "Beer", "bebidas", 168746, (("Botella", "Bottle"), 330), (("Lata", "Can"), 355)),
    a("vino", "Vino tinto", "Red wine", "bebidas", 2710688, (("Copa", "Glass"), 150)),
    a("aguardiente", "Aguardiente o ron (trago)", "Spirits (shot)", "bebidas", 174815, (("Trago", "Shot"), 30), aprox=True),
]
