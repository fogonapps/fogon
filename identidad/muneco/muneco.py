"""El muñeco de Fogón (Blender): un solo modelo en bloques (facetado, de pocas caras) que hace la acción de cada
app y los pasos del tutorial de El Forzudo. Todas las piezas miden lo mismo siempre; lo que se ve más grande es lo
que está más cerca de la cámara (perspectiva). Por personaje renderiza <nombre>-luz.png (con luz: las caras de los
bloques), <nombre>-cuerpo.png y <nombre>-cabeza.png (siluetas). Lo usa generar.py.
Uso: blender -b -P muneco.py -- <carpeta> [nombre…]"""
import math, sys
import bpy
from mathutils import Vector

SAL = sys.argv[sys.argv.index("--") + 1]

# Largo de cada hueso y su grosor al empezar y al terminar (iguales a los dos lados del cuerpo).
HUESOS = {
    "espalda": (0.62, 0.30, 0.23), "cuello": (0.10, 0.09, 0.09),
    "brazo": (0.34, 0.125, 0.10), "antebrazo": (0.32, 0.10, 0.09),
    "muslo": (0.50, 0.19, 0.15), "canilla": (0.48, 0.145, 0.11),
}
HOMBRO, CADERA = 0.20, 0.12
CABEZA = 0.40
CARAS = 5            # las piezas son prismas de cinco caras

bpy.ops.wm.read_factory_settings(use_empty=True)
esc = bpy.context.scene
esc.render.engine = "BLENDER_WORKBENCH"
esc.render.film_transparent = True
esc.render.resolution_x = esc.render.resolution_y = 1200
esc.view_settings.view_transform = "Standard"
cam = bpy.data.objects.new("camara", bpy.data.cameras.new("camara")); esc.collection.objects.link(cam); esc.camera = cam
# Angular, cerca y un poco abajo: lo que viene hacia la cámara crece, como en una foto.
cam.data.lens = 20
cam.location = (0.1, -2.6, 0.25)
cam.rotation_euler = (math.radians(92), 0, math.radians(2))


def material(nombre, color):
    m = bpy.data.materials.new(nombre); m.diffuse_color = color + (1,); return m


GRIS, CHISPA = material("cuerpo", (0.80, 0.79, 0.77)), material("cabeza", (0.81, 0.92, 0.23))


def unit(v): return Vector(v).normalized()


def plano(o, mat=None):
    if o.type == "MESH":
        for p in o.data.polygons: p.use_smooth = False
    o.data.materials.append(mat or GRIS); return o


def prisma(a, b, r0, r1, caras=CARAS):
    d = Vector(b) - Vector(a)
    bpy.ops.mesh.primitive_cone_add(radius1=r0, radius2=r1, depth=d.length, vertices=caras, location=(Vector(a) + Vector(b)) / 2)
    o = bpy.context.active_object; o.rotation_mode = "QUATERNION"; o.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d.normalized())
    return plano(o)


def bloque(c, tam, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=c, rotation=rot); o = bpy.context.active_object; o.scale = tam; return plano(o)


def gema(c, r, caras=6):
    """Una unión facetada (en vez de una esfera lisa)."""
    bpy.ops.mesh.primitive_ico_sphere_add(radius=r, subdivisions=1, location=c); return plano(bpy.context.active_object)


def disco(c, r, grosor, caras=12, rot=(math.radians(90), 0, 0)):
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=grosor, vertices=caras, location=c, rotation=rot)
    return plano(bpy.context.active_object)


def restar(o, cortador):
    m = o.modifiers.new("hueco", "BOOLEAN"); m.object = cortador; m.operation = "DIFFERENCE"; cortador.hide_render = True


def chispa(centro, r, gordura=0.12, grueso=0.03, nombre="cabeza", mat=None):
    puntas = [(0, 1), (1, 0), (0, -1), (-1, 0)]; pts = []
    for i in range(4):
        a, b = puntas[i], puntas[(i + 1) % 4]; c = ((a[0] + b[0]) * gordura, (a[1] + b[1]) * gordura)
        for k in range(16):
            s = k / 16
            pts.append(((1 - s) ** 2 * a[0] + 2 * (1 - s) * s * c[0] + s * s * b[0], (1 - s) ** 2 * a[1] + 2 * (1 - s) * s * c[1] + s * s * b[1]))
    me = bpy.data.meshes.new(nombre); n = len(pts)
    vs = [(x * r, -grueso, y * r) for x, y in pts] + [(x * r, grueso, y * r) for x, y in pts]
    me.from_pydata(vs, [], [list(range(n))[::-1], list(range(n, 2 * n))] + [[i, (i + 1) % n, n + (i + 1) % n, n + i] for i in range(n)])
    o = bpy.data.objects.new(nombre, me); esc.collection.objects.link(o); o.location = centro
    # La cabeza mira a la cámara.
    o.rotation_mode = "QUATERNION"; o.rotation_quaternion = Vector((0, -1, 0)).rotation_difference((cam.location - Vector(centro)).normalized())
    o.data.materials.append(mat or CHISPA); return o


class Muneco:
    def hueso(self, desde, direccion, nombre):
        largo, r0, r1 = HUESOS[nombre]
        hasta = Vector(desde) + unit(direccion) * largo
        prisma(desde, hasta, r0, r1); gema(desde, r0 * 0.95)
        return hasta

    def armar(self, pose, cadera=(0, 0, 0)):
        """pose: hacia dónde apunta cada hueso. 'adelante': cuánto se acerca a la cámara cada lado (perspectiva)."""
        pelvis = Vector(cadera)
        pecho = self.hueso(pelvis, pose["espalda"], "espalda")
        arriba = unit(pose["espalda"]); derecha = unit(arriba.cross(Vector((0, -1, 0)))) * -1
        if derecha.x < 0: derecha = -derecha
        base = self.hueso(pecho, pose["espalda"], "cuello")
        self.cabeza = base + arriba * (CABEZA * 0.9); self.manos = {}; self.pies = {}
        for lado, s in (("i", -1), ("d", 1)):
            cerca = Vector((0, -pose.get("adelante", {}).get(lado, 0), 0))
            hombro = pecho - arriba * 0.06 + derecha * HOMBRO * s
            codo = self.hueso(hombro, pose["brazo_" + lado], "brazo")
            self.manos[lado] = self.hueso(codo, pose["antebrazo_" + lado], "antebrazo")
            bloque(self.manos[lado], (0.13, 0.13, 0.13))
            cad = pelvis + derecha * CADERA * s + cerca
            rodilla = self.hueso(cad, pose["muslo_" + lado], "muslo")
            tobillo = self.hueso(rodilla, pose["canilla_" + lado], "canilla")
            pie = unit(pose.get("pie_" + lado, (1, 0, 0)))
            ang = math.atan2(pie.y, pie.x)
            self.pies[lado] = tobillo + pie * 0.07 + Vector((0, 0, -0.09))
            bloque(tobillo + pie * 0.07 + Vector((0, 0, -0.04)), (0.28, 0.15, 0.1), (0, 0, ang))


# ---------- Las cuatro acciones (el pie de adelante viene hacia la cámara) ----------

def el_forzudo(M):
    """Levanta la barra sobre la cabeza, de frente y con las dos piernas iguales (abiertas, firmes)."""
    M.armar({"espalda": (0, 0, 1),
             "brazo_i": (-0.75, 0, 1), "antebrazo_i": (-0.15, 0, 1), "brazo_d": (0.75, 0, 1), "antebrazo_d": (0.15, 0, 1),
             "muslo_i": (-0.45, 0, -1), "canilla_i": (-0.08, 0, -1), "pie_i": (-1, -0.3, 0),
             "muslo_d": (0.45, 0, -1), "canilla_d": (0.08, 0, -1), "pie_d": (1, -0.3, 0)})
    y, z = M.manos["i"].y, (M.manos["i"].z + M.manos["d"].z) / 2
    disco((0, y, z), 0.035, 2.3, 8, (0, math.radians(90), 0))
    for x in (-0.95, -0.83, 0.83, 0.95):
        disco((x, y, z), 0.27 if abs(x) < 0.9 else 0.2, 0.09, 10, (0, math.radians(90), 0))


def el_ahorrador(M):
    """Sísifo, como en las portadas: el cuerpo en diagonal, metido bajo una moneda más grande que él, los dos
    brazos arriba empujándola, la pierna de atrás estirada y la de adelante doblada. Sin cuesta: la inclinación
    del cuerpo dice que es cuesta arriba."""
    M.armar({"espalda": (0.8, 0, 0.75),
             "brazo_i": (0.9, 0.1, 0.55), "antebrazo_i": (1, 0.05, 0.25), "brazo_d": (0.95, -0.1, 0.4), "antebrazo_d": (1, -0.05, 0.15),
             "muslo_i": (-0.6, 0.15, -0.8), "canilla_i": (-0.85, 0.1, -0.55), "pie_i": (-0.4, 0, -1),
             "muslo_d": (0.55, -0.1, -0.85), "canilla_d": (-0.15, -0.05, -1), "pie_d": (1, -0.2, 0)},
            cadera=(-0.7, 0, -0.1))
    manos = (M.manos["i"] + M.manos["d"]) / 2
    r = 0.85
    c = manos + Vector((r * 0.95, 0, r * 0.25))
    moneda = disco(c, r, 0.16, 14)
    restar(moneda, chispa(c, r * 0.6, 0.16, 0.3, "calado"))


def la_cucha(M):
    """Corre con la cuchara de palo en alto como una antorcha; su gorro de chef flota encima de la estrella."""
    M.armar({"espalda": (0.35, 0, 1),
             "brazo_d": (1, 0, 0.75), "antebrazo_d": (0.7, 0, 1), "brazo_i": (-0.8, 0.2, -0.5), "antebrazo_i": (-0.6, 0, 0.5),
             "muslo_d": (0.6, -0.75, -0.5), "canilla_d": (0.1, -0.5, -1), "pie_d": (1, -0.5, 0), "adelante": {"d": 0.12},
             "muslo_i": (-0.5, 0.3, -1), "canilla_i": (-1, 0.2, -0.25), "pie_i": (-0.3, 0, -1)})
    # La cuchara de palo, en diagonal hacia afuera (así no se cruza con el gorro).
    mano = M.manos["d"]; d = Vector((0.45, 0, 1)).normalized()
    prisma(mano + d * 0.02, mano + d * 0.55, 0.035, 0.035)
    bpy.ops.mesh.primitive_ico_sphere_add(radius=0.11, subdivisions=1, location=mano + d * 0.66)
    o = plano(bpy.context.active_object); o.scale = (0.8, 0.5, 1.3)
    # El gorro de chef: la banda y la copa inflada, facetados, un poco arriba de la cabeza.
    g = M.cabeza + Vector((0, 0, CABEZA + 0.1))
    disco(g, 0.17, 0.12, 8, (0, 0, 0))
    for dx, dz, rr in ((-0.1, 0.16, 0.13), (0.1, 0.16, 0.13), (0, 0.22, 0.15)):
        bpy.ops.mesh.primitive_ico_sphere_add(radius=rr, subdivisions=1, location=g + Vector((dx, 0, dz))); plano(bpy.context.active_object)


def el_cajon(M):
    M.armar({"espalda": (0.45, 0, 1),
             "brazo_i": (-0.55, 0, 1), "antebrazo_i": (-0.35, 0, 1), "brazo_d": (1, -0.1, 0.15), "antebrazo_d": (1, 0, 0.35),
             "muslo_d": (0.8, -0.7, -0.35), "canilla_d": (0.5, -0.45, -0.9), "pie_d": (1, -0.5, 0), "adelante": {"d": 0.12},
             "muslo_i": (-0.85, 0.25, -0.55), "canilla_i": (-1, 0.1, 0.25), "pie_i": (-0.4, 0, 1)})
    c = M.manos["i"] + Vector((-0.12, 0, 0.25))
    d = disco(c, 0.36, 0.05, 14)
    restar(d, disco(c, 0.09, 0.4, 8))


# ---------- Los pasos del tutorial de El Forzudo ----------

DE_PIE = {"muslo_i": (-0.25, 0.05, -1), "canilla_i": (-0.05, 0, -1), "pie_i": (-1, 0, 0),
          "muslo_d": (0.3, -0.45, -1), "canilla_d": (0.05, -0.2, -1), "pie_d": (1, -0.4, 0), "adelante": {"d": 0.1}}


def tut_hola(M):
    """Saluda con la mano en alto y la otra en la cintura."""
    M.armar({"espalda": (0.05, 0, 1), "brazo_d": (0.8, 0, 0.9), "antebrazo_d": (0.15, 0, 1),
             "brazo_i": (-0.8, 0, -0.5), "antebrazo_i": (0.7, 0, -0.5), **DE_PIE})


def tut_anota(M):
    """Anota en su cuaderno: lo sostiene adelante con una mano y escribe con la otra."""
    M.armar({"espalda": (0.12, 0, 1), "brazo_d": (0.55, 0, -0.75), "antebrazo_d": (1, 0, 0.55),
             "brazo_i": (0.35, 0.1, -0.9), "antebrazo_i": (1, 0.1, 0.3), **DE_PIE})
    bloque(M.manos["d"] + Vector((0.2, -0.05, 0.12)), (0.4, 0.04, 0.5), (0, math.radians(-14), 0))
    prisma(M.manos["i"] + Vector((0.0, -0.08, 0.0)), M.manos["i"] + Vector((0.22, -0.08, 0.32)), 0.035, 0.02)


def tut_racha(M):
    """Corre a fondo: la racha se gana entrenando."""
    M.armar({"espalda": (0.6, 0, 1), "brazo_d": (0.9, 0, 0.3), "antebrazo_d": (0.3, 0, 1),
             "brazo_i": (-0.8, 0, -0.4), "antebrazo_i": (-0.6, 0, 0.5),
             "muslo_d": (0.8, -0.6, -0.3), "canilla_d": (0.1, -0.4, -1), "pie_d": (1, -0.3, 0), "adelante": {"d": 0.12},
             "muslo_i": (-0.4, 0.2, -1), "canilla_i": (-1, 0.1, 0.1), "pie_i": (-0.2, 0, 1)})


def tut_rangos(M):
    """En lo alto del podio, con los brazos arriba."""
    M.armar({"espalda": (0, 0, 1), "brazo_i": (-0.6, 0, 1), "antebrazo_i": (-0.3, 0, 1),
             "brazo_d": (0.6, 0, 1), "antebrazo_d": (0.3, 0, 1),
             "muslo_i": (-0.35, 0, -1), "canilla_i": (0, 0, -1), "pie_i": (-1, 0, 0),
             "muslo_d": (0.35, -0.3, -1), "canilla_d": (0, -0.1, -1), "pie_d": (1, -0.2, 0)}, cadera=(0, 0, 0.45))
    bloque((0, 0, -0.75), (1.0, 0.5, 0.42))


def tut_datos(M):
    """Camina con su caja al hombro: tus datos son tuyos y te los llevas."""
    M.armar({"espalda": (0.15, 0, 1), "brazo_d": (0.35, 0, 1), "antebrazo_d": (-0.15, 0, 1),
             "brazo_i": (-0.5, 0, -0.8), "antebrazo_i": (-0.1, 0, -1),
             "muslo_d": (0.55, -0.5, -0.85), "canilla_d": (0.05, -0.3, -1), "pie_d": (1, -0.3, 0), "adelante": {"d": 0.1},
             "muslo_i": (-0.45, 0.15, -0.9), "canilla_i": (-0.5, 0.1, -0.8), "pie_i": (0.2, 0, -1)})
    bloque(M.manos["d"] + Vector((0.42, 0, 0.02)), (0.62, 0.4, 0.44))


# La cabeza de cada uno, en el color de su app (en la app se tiñe otra vez; esto es para la vista con luz).
COLOR = {"el-forzudo": "#C4E02E", "el-ahorrador": "#F2384D", "la-cucha": "#FF8A4C", "el-cajon": "#9B7BFF",
         **{f"tut-{p}": "#C4E02E" for p in ("hola", "anota", "racha", "rangos", "datos")}}
ACCIONES = {"el-forzudo": el_forzudo, "el-ahorrador": el_ahorrador, "la-cucha": la_cucha, "el-cajon": el_cajon,
            "tut-hola": tut_hola, "tut-anota": tut_anota, "tut-racha": tut_racha, "tut-rangos": tut_rangos, "tut-datos": tut_datos}
SOLO = sys.argv[sys.argv.index("--") + 2:] or list(ACCIONES)

sh = esc.display.shading
for nombre in SOLO:
    for o in list(bpy.data.objects):
        if o.type != "CAMERA": bpy.data.objects.remove(o)
    M = Muneco(); ACCIONES[nombre](M)
    # La cabeza en el color de la app (sRGB → lineal, que es lo que usa Blender).
    hexa = COLOR[nombre]; srgb = [int(hexa[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    CHISPA.diffuse_color = tuple(c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in srgb) + (1,)
    cab = chispa(M.cabeza, CABEZA)
    # Con luz: para ver la forma.
    sh.light = "STUDIO"; sh.color_type = "MATERIAL"; sh.show_cavity = True; sh.cavity_type = "BOTH"; sh.show_shadows = False
    esc.render.filepath = f"{SAL}/{nombre}-luz.png"; bpy.ops.render.render(write_still=True)
    # Siluetas para la app: el cuerpo (sin la cabeza) y la cabeza sola.
    sh.light = "FLAT"; sh.color_type = "SINGLE"; sh.single_color = (0, 0, 0); sh.show_cavity = False
    cab.hide_render = True
    esc.render.filepath = f"{SAL}/{nombre}-cuerpo.png"; bpy.ops.render.render(write_still=True)
    cab.hide_render = False
    ocultos = [o for o in bpy.data.objects if o not in (cab, cam) and not o.hide_render]
    for o in ocultos: o.hide_render = True
    esc.render.filepath = f"{SAL}/{nombre}-cabeza.png"; bpy.ops.render.render(write_still=True)
print("LISTO")
