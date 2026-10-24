"""Ilustraciones 3D de Fogón, hechas desde cero en Blender (sin modelos de nadie más).

Uso (desde la raíz del repo):
    blender --background --factory-startup --python identidad/vidrio/ilustraciones/generar.py -- <carpeta de salida> [nombre…]

Cada objeto se modela con primitivas y modificadores, se pinta de un plástico blanco brillante y se
renderiza con Cycles sobre fondo transparente, en 512 × 512. Salen en gris: la app los tiñe con la tinta
del tema (ver `Ilustracion` en core/identidad), así una sola imagen sirve para todos los temas.
"""
import math
import sys

import bpy

LADO = 512
MUESTRAS = 96


def limpiar():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def material_blanco():
    m = bpy.data.materials.new("plastico")
    m.use_nodes = True
    p = m.node_tree.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = (0.7, 0.7, 0.7, 1)
    p.inputs["Roughness"].default_value = 0.22
    p.inputs["Metallic"].default_value = 0.15
    p.inputs["Coat Weight"].default_value = 0.6
    p.inputs["Coat Roughness"].default_value = 0.08
    return m


def suavizar(obj, niveles=2, bisel=0.0):
    if bisel > 0:
        b = obj.modifiers.new("bisel", "BEVEL")
        b.width = bisel
        b.segments = 3
        b.limit_method = "ANGLE"
    s = obj.modifiers.new("suave", "SUBSURF")
    s.levels = niveles
    s.render_levels = niveles
    for p in obj.data.polygons:
        p.use_smooth = True


def torno(nombre, perfil, pasos=96, grosor=0.0):
    """Un sólido de revolución alrededor de Z a partir de un perfil [(radio, z), …]."""
    malla = bpy.data.meshes.new(nombre)
    verts = [(r, 0.0, z) for r, z in perfil]
    aristas = [(i, i + 1) for i in range(len(verts) - 1)]
    malla.from_pydata(verts, aristas, [])
    obj = bpy.data.objects.new(nombre, malla)
    bpy.context.collection.objects.link(obj)
    t = obj.modifiers.new("torno", "SCREW")
    t.angle = math.tau
    t.steps = pasos
    t.render_steps = pasos
    t.use_merge_vertices = True
    if grosor:
        g = obj.modifiers.new("grosor", "SOLIDIFY")
        g.thickness = grosor
    s = obj.modifiers.new("suave", "SUBSURF")
    s.levels = 2
    s.render_levels = 2
    return obj


def cilindro(radio, alto, ubicacion=(0, 0, 0), rotacion=(0, 0, 0), vertices=64):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radio, depth=alto, location=ubicacion, rotation=rotacion)
    return bpy.context.active_object


def unir(objetos, nombre):
    for o in objetos:
        bpy.context.view_layer.objects.active = o
        for m in list(o.modifiers):
            bpy.ops.object.modifier_apply(modifier=m.name)
    bpy.ops.object.select_all(action="DESELECT")
    for o in objetos:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objetos[0]
    bpy.ops.object.join()
    obj = bpy.context.active_object
    obj.name = nombre
    # Deja el giro de las piezas dentro de la malla, para poder girar el objeto entero después.
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    return obj


def texto(cadena, tam, alto):
    bpy.ops.object.text_add()
    t = bpy.context.active_object
    t.data.body = cadena
    t.data.size = tam
    t.data.extrude = alto
    t.data.bevel_depth = alto * 0.35
    t.data.align_x = "CENTER"
    t.data.align_y = "CENTER"
    bpy.ops.object.convert(target="MESH")
    return bpy.context.active_object


# ----- Los objetos -----

def trofeo():
    copa = torno("copa", [(0.0, 0.55), (0.62, 0.62), (0.66, 1.05), (0.70, 1.55), (0.64, 1.6)], grosor=0.06)
    pie = torno("pie", [(0.0, -0.32), (0.55, -0.32), (0.55, -0.12), (0.2, -0.05), (0.12, 0.25), (0.22, 0.55), (0.0, 0.58)])
    asas = []
    for lado in (-1, 1):
        bpy.ops.mesh.primitive_torus_add(major_radius=0.3, minor_radius=0.07, location=(lado * 0.68, 0, 1.15), rotation=(math.pi / 2, 0, 0))
        a = bpy.context.active_object
        a.scale = (0.9, 1, 1.3)
        suavizar(a, 1)
        asas.append(a)
    estrella = texto("★", 0.55, 0.05)
    estrella.rotation_euler = (math.pi / 2, 0, 0)
    estrella.location = (0, -0.66, 1.08)
    return unir([copa, pie, estrella] + asas, "trofeo")


def mancuerna():
    partes = [cilindro(0.09, 1.6, rotacion=(0, math.pi / 2, 0))]
    for lado in (-1, 1):
        for i, (r, ancho) in enumerate([(0.42, 0.14), (0.36, 0.12)]):
            x = lado * (0.55 + i * 0.15)
            p = cilindro(r, ancho, ubicacion=(x, 0, 0), rotacion=(0, math.pi / 2, 0))
            suavizar(p, 2, 0.03)
            partes.append(p)
        tope = cilindro(0.14, 0.1, ubicacion=(lado * 0.82, 0, 0), rotacion=(0, math.pi / 2, 0))
        suavizar(tope, 1, 0.02)
        partes.append(tope)
    suavizar(partes[0], 1)
    m = unir(partes, "mancuerna")
    m.rotation_euler = (math.radians(20), math.radians(-15), math.radians(25))
    return m


def moneda(signo=True):
    cuerpo = cilindro(0.7, 0.14, rotacion=(math.pi / 2, 0, 0))
    suavizar(cuerpo, 2, 0.04)
    partes = [cuerpo]
    for y in (-0.075, 0.075):
        bpy.ops.mesh.primitive_torus_add(major_radius=0.6, minor_radius=0.035, location=(0, y, 0), rotation=(math.pi / 2, 0, 0))
        partes.append(bpy.context.active_object)
    if signo:
        s = texto("$", 0.85, 0.04)
        s.rotation_euler = (math.pi / 2, 0, 0)
        s.location = (0, -0.09, 0.0)
        partes.append(s)
    m = unir(partes, "moneda")
    m.rotation_euler = (math.radians(8), 0, math.radians(-28))
    return m


def monedas():
    pila = []
    for i in range(4):
        c = cilindro(0.55, 0.12, ubicacion=(0.04 * (i % 2), 0.03 * i, -0.5 + i * 0.13))
        suavizar(c, 2, 0.03)
        pila.append(c)
    parada = moneda()
    parada.location = (0.62, 0.2, 0.05)
    parada.rotation_euler = (math.radians(8), 0, math.radians(-40))
    bpy.context.view_layer.objects.active = parada
    for m in list(parada.modifiers):
        bpy.ops.object.modifier_apply(modifier=m.name)
    return unir(pila + [parada], "monedas")


def sobre():
    bpy.ops.mesh.primitive_cube_add(size=2)
    caja = bpy.context.active_object
    caja.scale = (0.85, 0.08, 0.58)
    bpy.ops.object.transform_apply(scale=True)
    suavizar(caja, 2, 0.05)
    malla = bpy.data.meshes.new("solapa")
    malla.from_pydata([(-0.8, 0, 0.54), (0.8, 0, 0.54), (0, 0, -0.08)], [], [(0, 1, 2)])
    solapa = bpy.data.objects.new("solapa", malla)
    bpy.context.collection.objects.link(solapa)
    solapa.location = (0, -0.09, 0)
    g = solapa.modifiers.new("grosor", "SOLIDIFY")
    g.thickness = 0.05
    suavizar(solapa, 2, 0.02)
    m = unir([caja, solapa], "sobre")
    m.rotation_euler = (math.radians(10), 0, math.radians(-22))
    return m


OBJETOS = {"trofeo": trofeo, "mancuerna": mancuerna, "moneda": moneda, "monedas": monedas, "sobre": sobre}


# ----- La escena -----

def escena(obj):
    obj.data.materials.append(material_blanco())
    # Centrar y escalar para que quepa en el cuadro.
    bpy.context.view_layer.update()
    esquinas = [obj.matrix_world @ v.co for v in obj.data.vertices]
    mn = [min(c[i] for c in esquinas) for i in range(3)]
    mx = [max(c[i] for c in esquinas) for i in range(3)]
    centro = [(a + b) / 2 for a, b in zip(mn, mx)]
    obj.location = [obj.location[i] - centro[i] for i in range(3)]
    escala = 2.9 / max(mx[i] - mn[i] for i in range(3))
    obj.scale = [escala] * 3

    bpy.ops.object.camera_add(location=(0, -6.2, 1.6), rotation=(math.radians(76), 0, 0))
    cam = bpy.context.active_object
    cam.data.lens = 55
    bpy.context.scene.camera = cam

    def luz(nombre, ubicacion, energia, tam, color=(1, 1, 1)):
        bpy.ops.object.light_add(type="AREA", location=ubicacion)
        l = bpy.context.active_object
        l.data.energy = energia
        l.data.size = tam
        l.data.color = color
        d = obj.location - l.location
        l.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
        return l

    # Una luz fuerte arriba a la izquierda, poco relleno y un borde atrás: volumen claro para teñir.
    luz("clave", (-3.0, -3.5, 4.0), 260, 2.5)
    luz("relleno", (3.5, -2.5, 0.5), 40, 4.0)
    luz("borde", (2.0, 3.5, 3.0), 300, 1.5)

    mundo = bpy.data.worlds.new("mundo")
    mundo.use_nodes = True
    mundo.node_tree.nodes["Background"].inputs["Color"].default_value = (0.25, 0.25, 0.25, 1)
    mundo.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.25
    bpy.context.scene.world = mundo

    r = bpy.context.scene.render
    r.engine = "CYCLES"
    r.resolution_x = r.resolution_y = LADO
    r.film_transparent = True
    r.image_settings.file_format = "PNG"
    r.image_settings.color_mode = "RGBA"
    c = bpy.context.scene.cycles
    c.samples = MUESTRAS
    c.use_denoising = True
    try:
        prefs = bpy.context.preferences.addons["cycles"].preferences
        prefs.compute_device_type = "CUDA"
        prefs.get_devices()
        for d in prefs.devices:
            d.use = True
        c.device = "GPU"
    except Exception:
        c.device = "CPU"
    bpy.context.scene.view_settings.view_transform = "Standard"


def main():
    args = sys.argv[sys.argv.index("--") + 1:]
    salida = args[0]
    nombres = args[1:] or list(OBJETOS)
    for n in nombres:
        limpiar()
        obj = OBJETOS[n]()
        escena(obj)
        bpy.context.scene.render.filepath = f"{salida}/{n}.png"
        bpy.ops.render.render(write_still=True)
        print("listo", n)


main()
