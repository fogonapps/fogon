"""Un solo muñeco de Fogón en 3D (Blender) que hace la acción de cada app. Mismas proporciones siempre: cada
hueso tiene su largo y su grosor fijos y la pose solo dice hacia dónde apunta. Se renderiza la silueta (sin
luz, de un color) en dos pasadas: el cuerpo con sus objetos y la cabeza (la chispa) sola.
Uso: blender -b -P muneco.py -- <carpeta de salida>"""
import math, sys
import bpy
from mathutils import Vector, Quaternion

SAL = sys.argv[sys.argv.index("--") + 1]

# Largo de cada hueso y grosor (radio) al empezar y al terminar.
HUESOS = {
    "espalda": (0.62, 0.20, 0.17), "cuello": (0.12, 0.08, 0.08),
    "brazo": (0.34, 0.10, 0.08), "antebrazo": (0.32, 0.08, 0.065),
    "muslo": (0.50, 0.17, 0.12), "canilla": (0.48, 0.115, 0.08),
}
HOMBRO, CADERA = 0.19, 0.11
CABEZA = 0.42          # radio de la chispa que hace de cabeza

# ---------- La escena ----------
bpy.ops.wm.read_factory_settings(use_empty=True)
esc = bpy.context.scene
esc.render.engine = "BLENDER_WORKBENCH"
esc.display.shading.light = "FLAT"
esc.display.shading.color_type = "SINGLE"
esc.display.shading.single_color = (0, 0, 0)
esc.render.film_transparent = True
esc.render.resolution_x = esc.render.resolution_y = 1200
esc.view_settings.view_transform = "Standard"

cam = bpy.data.objects.new("camara", bpy.data.cameras.new("camara"))
esc.collection.objects.link(cam); esc.camera = cam
cam.data.lens = 30
# Un poco baja y cerca: lo que viene hacia la cámara (una pierna) se ve más grande.
cam.location = (0, -3.3, 0.35)
cam.rotation_euler = (math.radians(90 - 3), 0, 0)


def unit(v): v = Vector(v); return v.normalized()


class Muneco:
    """Cada hueso es un cono que se adelgaza, con una esfera en cada punta (así las uniones quedan redondas)."""
    def bola(self, p, r): esfera("bola", r, p)

    def hueso(self, desde, direccion, nombre, escala=1.0):
        largo, r0, r1 = HUESOS[nombre]
        d = unit(direccion); hasta = Vector(desde) + d * largo
        bpy.ops.mesh.primitive_cone_add(radius1=r0 * escala, radius2=r1 * escala, depth=largo, vertices=48,
                                        location=(Vector(desde) + hasta) / 2)
        bpy.context.active_object.rotation_mode = "QUATERNION"
        bpy.context.active_object.rotation_quaternion = Vector((0, 0, 1)).rotation_difference(d)
        self.bola(Vector(desde), r0 * escala); self.bola(hasta, r1 * escala)
        return hasta

    def malla(self, nombre, crear, **k):
        crear(**k); o = bpy.context.active_object; o.name = nombre; self.cuerpo.append(o); return o

    def armar(self, pose, cadera=(0, 0, 0)):
        """pose: dirección de cada hueso; 'grande' agranda una pierna (la que viene hacia la cámara)."""
        pelvis = Vector(cadera)
        pecho = self.hueso(pelvis, pose["espalda"], "espalda")
        arriba = unit(pose["espalda"])
        derecha = unit(arriba.cross(Vector((0, -1, 0)))) * -1
        if derecha.x < 0: derecha = -derecha
        base = self.hueso(pecho, pose["espalda"], "cuello")
        self.cabeza = base + arriba * (CABEZA * 0.95)
        self.manos = {}
        for lado, s in (("i", -1), ("d", 1)):
            hombro = pecho - arriba * 0.06 + derecha * HOMBRO * s
            codo = self.hueso(hombro, pose["brazo_" + lado], "brazo")
            self.manos[lado] = self.hueso(codo, pose["antebrazo_" + lado], "antebrazo")
            self.bola(self.manos[lado], 0.075)
            cad = pelvis + derecha * CADERA * s
            g = pose.get("grande") == lado and 1.6 or 1.0
            rodilla = self.hueso(cad, pose["muslo_" + lado], "muslo", g)
            tobillo = self.hueso(rodilla, pose["canilla_" + lado], "canilla", g)
            # El pie: un óvalo hacia adelante.
            pie = unit(pose.get("pie_" + lado, (1, 0, 0)))
            for i in range(5):
                self.bola(tobillo + pie * (0.035 * i), (0.075 - 0.006 * i) * g)


def cilindro(nombre, r, alto, lugar, rot=(0, 0, 0), vertices=64):
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=alto, location=lugar, rotation=rot, vertices=vertices)
    o = bpy.context.active_object; o.name = nombre; return o


def esfera(nombre, r, lugar, escala=(1, 1, 1)):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=r, location=lugar, segments=48, ring_count=24)
    o = bpy.context.active_object; o.name = nombre; o.scale = escala; return o


def chispa(nombre, centro, r, gordura=0.12, grueso=0.02):
    """La chispa de Fogón (cuatro curvas cuadráticas) como placa de frente a la cámara."""
    puntas = [(0, 1), (1, 0), (0, -1), (-1, 0)]
    pts = []
    for i in range(4):
        a, b = puntas[i], puntas[(i + 1) % 4]
        c = ((a[0] + b[0]) * gordura, (a[1] + b[1]) * gordura)
        for k in range(16):
            s = k / 16
            pts.append(((1 - s) ** 2 * a[0] + 2 * (1 - s) * s * c[0] + s * s * b[0],
                        (1 - s) ** 2 * a[1] + 2 * (1 - s) * s * c[1] + s * s * b[1]))
    me = bpy.data.meshes.new(nombre)
    vs = [(x * r, -grueso, y * r) for x, y in pts] + [(x * r, grueso, y * r) for x, y in pts]
    n = len(pts)
    caras = [list(range(n))[::-1], list(range(n, 2 * n))] + [[i, (i + 1) % n, n + (i + 1) % n, n + i] for i in range(n)]
    me.from_pydata(vs, [], caras); me.update()
    o = bpy.data.objects.new(nombre, me); esc.collection.objects.link(o); o.location = centro
    return o


def restar(obj, cortador):
    m = obj.modifiers.new("hueco", "BOOLEAN"); m.object = cortador; m.operation = "DIFFERENCE"
    cortador.hide_render = True


# ---------- Las cuatro acciones ----------

def forzudo(M):
    M.armar({
        "espalda": (0, 0, 1),
        "brazo_i": (-0.75, 0, 1), "antebrazo_i": (-0.15, 0, 1), "brazo_d": (0.75, 0, 1), "antebrazo_d": (0.15, 0, 1),
        "muslo_i": (-0.55, 0.25, -1), "canilla_i": (-0.15, 0.2, -1), "pie_i": (-1, 0, 0),
        "muslo_d": (0.7, -0.3, -0.8), "canilla_d": (0.15, -0.2, -1), "pie_d": (1, -0.2, 0), "grande": "d",
    })
    y = M.manos["i"].z
    cilindro("barra", 0.035, 2.3, (0, M.manos["i"].y, y), (0, math.radians(90), 0))
    for x in (-0.95, -0.82, 0.82, 0.95):
        r = 0.27 if abs(x) < 0.9 else 0.2
        cilindro("disco", r, 0.09, (x, M.manos["i"].y, y), (0, math.radians(90), 0))


def ahorrador(M):
    M.armar({
        "espalda": (0.95, 0, 0.75),
        "brazo_i": (1, 0.1, 0.1), "antebrazo_i": (1, 0, 0.25), "brazo_d": (1, -0.1, -0.15), "antebrazo_d": (1, 0, 0.1),
        "muslo_i": (-0.9, 0.2, -0.75), "canilla_i": (-1, 0.1, -0.55), "pie_i": (-0.2, 0, -1),
        "muslo_d": (0.6, -0.3, -0.85), "canilla_d": (-0.05, -0.2, -1), "pie_d": (1, -0.4, 0), "grande": "d",
    }, cadera=(-0.75, 0, 0))
    centro = (M.manos["i"] + M.manos["d"]) / 2 + Vector((0.62, 0, 0.0))
    moneda = cilindro("moneda", 0.62, 0.12, centro, (math.radians(90), 0, 0))
    restar(moneda, chispa("calado", centro, 0.38, 0.16, 0.2))


def cucha(M):
    M.armar({
        "espalda": (0.35, 0, 1),
        "brazo_d": (0.35, 0, 1), "antebrazo_d": (0.1, 0, 1), "brazo_i": (-0.8, 0.2, -0.5), "antebrazo_i": (-0.6, 0, 0.5),
        "muslo_d": (0.85, -0.3, -0.5), "canilla_d": (0.15, -0.2, -1), "pie_d": (1, -0.3, 0), "grande": "d",
        "muslo_i": (-0.5, 0.3, -1), "canilla_i": (-1, 0.2, -0.25), "pie_i": (-0.3, 0, -1),
    })
    mano = M.manos["d"]
    cilindro("palo", 0.03, 0.55, mano + Vector((0.02, 0, 0.27)))
    esfera("cuchara", 0.1, mano + Vector((0.02, 0, 0.6)), (0.8, 0.5, 1.3))
    # La falda: un cono que se abre y vuela hacia atrás.
    bpy.ops.mesh.primitive_cone_add(radius1=0.55, radius2=0.2, depth=0.6, location=(-0.12, 0.02, -0.05), rotation=(0, math.radians(-32), 0), vertices=64)


def cajon(M):
    M.armar({
        "espalda": (0.45, 0, 1),
        "brazo_i": (-0.55, 0, 1), "antebrazo_i": (-0.35, 0, 1), "brazo_d": (1, -0.1, 0.15), "antebrazo_d": (1, 0, 0.35),
        "muslo_d": (1, -0.3, -0.35), "canilla_d": (0.55, -0.2, -0.9), "pie_d": (1, -0.2, 0), "grande": "d",
        "muslo_i": (-0.85, 0.25, -0.55), "canilla_i": (-1, 0.1, 0.25), "pie_i": (-0.4, 0, 1),
    })
    centro = M.manos["i"] + Vector((-0.12, 0, 0.25))
    disco = cilindro("disco", 0.36, 0.06, centro, (math.radians(90), 0, 0))
    restar(disco, cilindro("hueco", 0.09, 0.4, centro, (math.radians(90), 0, 0)))


# ---------- Los muñequitos del tutorial de El Forzudo ----------

DE_PIE = {"muslo_i": (-0.25, 0.05, -1), "canilla_i": (-0.05, 0, -1), "pie_i": (-1, 0, 0),
          "muslo_d": (0.25, -0.05, -1), "canilla_d": (0.05, 0, -1), "pie_d": (1, 0, 0)}


def tut_hola(M):
    """Saluda con la mano en alto y la otra en la cintura."""
    M.armar({"espalda": (0.05, 0, 1), "brazo_d": (0.8, 0, 0.9), "antebrazo_d": (0.15, 0, 1),
             "brazo_i": (-0.8, 0, -0.5), "antebrazo_i": (0.7, 0, -0.5), **DE_PIE, "grande": "d"})


def tut_anota(M):
    """Anota en su cuaderno: lo sostiene adelante con una mano y escribe con la otra (de perfil)."""
    M.armar({"espalda": (0.12, 0, 1), "brazo_d": (0.55, 0, -0.75), "antebrazo_d": (1, 0, 0.55),
             "brazo_i": (0.35, 0.1, -0.9), "antebrazo_i": (1, 0.1, 0.3),
             "muslo_d": (0.3, -0.1, -1), "canilla_d": (0.05, 0, -1), "pie_d": (1, 0, 0), "grande": "d",
             "muslo_i": (-0.3, 0.1, -1), "canilla_i": (-0.1, 0, -1), "pie_i": (1, 0, 0)})
    centro = M.manos["d"] + Vector((0.2, -0.05, 0.12))
    bpy.ops.mesh.primitive_cube_add(size=1, location=centro, rotation=(0, math.radians(-14), 0))
    bpy.context.active_object.scale = (0.4, 0.03, 0.5)
    cilindro("lapiz", 0.032, 0.42, M.manos["i"] + Vector((0.08, -0.08, 0.14)), (0, math.radians(38), 0))


def tut_racha(M):
    """Corre a fondo: la racha se gana entrenando."""
    M.armar({"espalda": (0.6, 0, 1), "brazo_d": (0.9, 0, 0.3), "antebrazo_d": (0.3, 0, 1),
             "brazo_i": (-0.8, 0, -0.4), "antebrazo_i": (-0.6, 0, 0.5),
             "muslo_d": (0.9, -0.3, -0.3), "canilla_d": (0.1, -0.2, -1), "pie_d": (1, 0, 0), "grande": "d",
             "muslo_i": (-0.4, 0.2, -1), "canilla_i": (-1, 0.1, 0.1), "pie_i": (-0.2, 0, 1)})


def tut_rangos(M):
    """En lo alto del podio, con los brazos arriba."""
    M.armar({"espalda": (0, 0, 1), "brazo_i": (-0.6, 0, 1), "antebrazo_i": (-0.3, 0, 1),
             "brazo_d": (0.6, 0, 1), "antebrazo_d": (0.3, 0, 1),
             "muslo_i": (-0.35, 0, -1), "canilla_i": (0, 0, -1), "pie_i": (-1, 0, 0),
             "muslo_d": (0.35, 0, -1), "canilla_d": (0, 0, -1), "pie_d": (1, 0, 0), "grande": "d"}, cadera=(0, 0, 0.45))
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, -0.75)); bpy.context.active_object.scale = (1.0, 0.5, 0.42)


def tut_datos(M):
    """Camina con su caja al hombro: tus datos son tuyos y te los llevas."""
    M.armar({"espalda": (0.15, 0, 1), "brazo_d": (0.35, 0, 1), "antebrazo_d": (-0.15, 0, 1),
             "brazo_i": (-0.5, 0, -0.8), "antebrazo_i": (-0.1, 0, -1),
             "muslo_d": (0.55, -0.15, -0.85), "canilla_d": (0.05, -0.1, -1), "pie_d": (1, 0, 0), "grande": "d",
             "muslo_i": (-0.45, 0.15, -0.9), "canilla_i": (-0.5, 0.1, -0.8), "pie_i": (0.2, 0, -1)})
    bpy.ops.mesh.primitive_cube_add(size=1, location=M.manos["d"] + Vector((0.42, 0, 0.02)))
    bpy.context.active_object.scale = (0.62, 0.4, 0.44)


ACCIONES = {"el-forzudo": forzudo, "el-ahorrador": ahorrador, "la-cucha": cucha, "el-cajon": cajon,
            "tut-hola": tut_hola, "tut-anota": tut_anota, "tut-racha": tut_racha, "tut-rangos": tut_rangos, "tut-datos": tut_datos}

for nombre, accion in ACCIONES.items():
    for o in list(bpy.data.objects):
        if o.type != "CAMERA": bpy.data.objects.remove(o)
    M = Muneco(); accion(M)
    cab = chispa("cabeza", M.cabeza, CABEZA, 0.12)
    # Pasada 1: el cuerpo y sus objetos (sin la cabeza).
    cab.hide_render = True
    esc.render.filepath = f"{SAL}/cuerpo-{nombre}.png"; bpy.ops.render.render(write_still=True)
    # Pasada 2: la cabeza sola.
    cab.hide_render = False
    for o in bpy.data.objects:
        if o not in (cab, cam): o.hide_render = True
    esc.render.filepath = f"{SAL}/cabeza-{nombre}.png"; bpy.ops.render.render(write_still=True)
print("LISTO")
