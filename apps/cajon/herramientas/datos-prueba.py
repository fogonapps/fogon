#!/usr/bin/env python3
"""Datos de prueba de El Cajón, inventados (nunca los de alguien), para el emulador y las capturas.

Uso (desde apps/cajon): python3 herramientas/datos-prueba.py <es|en> <AAAA-MM-DD de hoy> <salida.json>
Escribe un Estado con películas, series, animes, libros y álbumes (con notas, diario, episodios vistos,
páginas y canciones calificadas), dos listas, las cuatro favoritas y los retos del año. Sin portadas: las
de verdad tienen dueño y no van en las capturas. Para usarlo en el emulador (versión de prueba):
adb push … y `run-as io.github.fogonapps.cajon cp … files/datastore/estado.json`.
"""
import datetime as dt
import json
import random
import sys

idioma, hoy, salida = sys.argv[1], dt.date.fromisoformat(sys.argv[2]), sys.argv[3]
es = idioma == 'es'
random.seed(11)
ahora = int(dt.datetime.combine(hoy, dt.time(10)).timestamp() * 1000)
n = 0


def nid(p):
    global n
    n += 1
    return f'{p}{n}'


def fecha(dias_atras):
    return (hoy - dt.timedelta(days=dias_atras)).isoformat()


def obra(tipo, titulo, anio, creador, generos, estado='terminado', nota=0, megusta=False, atras=None, **extra):
    o = {'id': nid('o'), 'tipo': tipo, 'titulo': titulo, 'anio': anio, 'creador': creador, 'generos': generos,
         'estado': estado, 'nota': nota, 'meGusta': megusta, 'agregada': ahora - n * 3600000, 'cambio': ahora - n * 3600000}
    if estado == 'terminado':
        f = fecha(atras if atras is not None else random.randint(2, 280))
        o['terminada'] = f
        o['registros'] = [{'id': nid('g'), 'fecha': f, 'nota': nota}]
    o.update(extra)
    return o


G = (lambda e, i: e if es else i)
obras = [
    obra('pelicula', 'Nosferatu', 1922, 'F. W. Murnau', [G('Terror', 'Horror')], nota=9, megusta=True, atras=3, minutos=94,
         resena=G('La sombra subiendo por la escalera todavía da miedo, cien años después.', 'The shadow climbing the stairs is still scary a hundred years later.')),
    obra('pelicula', 'Metropolis', 1927, 'Fritz Lang', [G('Ciencia ficción', 'Science fiction')], nota=8, atras=12, minutos=153),
    obra('pelicula', 'The General', 1926, 'Buster Keaton', [G('Comedia', 'Comedy')], nota=10, megusta=True, atras=20, minutos=78),
    obra('pelicula', 'La pasión de Juana de Arco' if es else 'The Passion of Joan of Arc', 1928, 'Carl Theodor Dreyer', ['Drama'], nota=9, atras=34, minutos=82),
    obra('pelicula', 'Sherlock Jr.', 1924, 'Buster Keaton', [G('Comedia', 'Comedy')], nota=8, atras=41, minutos=45),
    obra('pelicula', 'El gabinete del doctor Caligari' if es else 'The Cabinet of Dr. Caligari', 1920, 'Robert Wiene', [G('Terror', 'Horror')], estado='quiero'),
    obra('serie', G('La casa del río', 'The River House'), 2024, 'Ana Rivas', ['Drama'], estado='en_curso',
         temporadas=[{'numero': 1, 'episodios': 8}, {'numero': 2, 'episodios': 8}], vistos=[f'1x{i}' for i in range(1, 9)] + ['2x1', '2x2', '2x3'], empezada=fecha(30)),
    obra('serie', G('Barrio arriba', 'Uptown'), 2022, 'Tomás Ortega', [G('Comedia', 'Comedy')], nota=7, atras=60,
         temporadas=[{'numero': 1, 'episodios': 10}], vistos=[f'1x{i}' for i in range(1, 11)]),
    obra('anime', G('Cielo de papel', 'Paper Sky'), 2023, 'Studio Faro', [G('Fantasía', 'Fantasy')], estado='en_curso',
         temporadas=[{'numero': 1, 'episodios': 12}], vistos=[f'1x{i}' for i in range(1, 6)], empezada=fecha(10)),
    obra('anime', G('El último tren', 'The Last Train'), 2021, 'Studio Faro', ['Drama'], nota=9, megusta=True, atras=90,
         temporadas=[{'numero': 1, 'episodios': 13}], vistos=[f'1x{i}' for i in range(1, 14)]),
    obra('libro', 'Don Quijote de la Mancha' if es else 'Don Quixote', 1605, 'Miguel de Cervantes', ['Novela' if es else 'Novel'], estado='en_curso', paginas=1056, pagina=412, empezada=fecha(45)),
    obra('libro', 'María', 1867, 'Jorge Isaacs', ['Novela' if es else 'Novel', 'Romance'], nota=8, atras=8, paginas=360),
    obra('libro', 'Frankenstein', 1818, 'Mary Shelley', [G('Terror', 'Horror')], nota=9, megusta=True, atras=50, paginas=280),
    obra('libro', 'La vorágine' if es else 'The Vortex', 1924, 'José Eustasio Rivera', ['Novela' if es else 'Novel'], nota=7, atras=120, paginas=320),
    obra('libro', 'Orgullo y prejuicio' if es else 'Pride and Prejudice', 1813, 'Jane Austen', ['Romance'], estado='quiero', paginas=432),
    obra('album', G('Cumbias del río', 'River Cumbias'), 2019, G('Los del Puerto', 'Los del Puerto'), ['Cumbia'], nota=9, megusta=True, atras=1, minutos=38,
         canciones=[{'titulo': t, 'segundos': s, 'nota': k, 'favorita': f} for t, s, k, f in [
             (G('La subienda', 'The Run'), 214, 9, True), (G('Puerto de noche', 'Port at Night'), 251, 8, False),
             (G('Canoa', 'Canoe'), 198, 10, True), (G('Lluvia en Mompox', 'Rain in Mompox'), 263, 7, False),
             (G('El pescador', 'The Fisherman'), 230, 8, False), (G('Atardecer', 'Sunset'), 245, 9, False)]],
         formato='Album'),
    obra('album', G('Ciudad de vidrio', 'Glass City'), 2021, 'Nadia Sol', ['Indie pop'], nota=8, atras=15, minutos=41,
         canciones=[{'titulo': t, 'segundos': 200 + i * 7} for i, t in enumerate([G('Ventanas', 'Windows'), G('Neón', 'Neon'), G('Afuera', 'Outside'), G('Volver', 'Back')])]),
    obra('album', G('Bambuco eléctrico', 'Electric Bambuco'), 2018, 'Trío Andino', ['Folk'], nota=7, atras=70, minutos=35),
    obra('album', G('Lo que no se dijo', 'Unsaid'), 2024, 'Nadia Sol', ['Indie pop'], estado='quiero'),
]
# Algunas se vieron otra vez.
obras[0]['registros'].append({'id': nid('g'), 'fecha': fecha(200), 'nota': 9, 'otraVez': False})
obras[0]['registros'][0]['otraVez'] = True

por = {o['titulo']: o['id'] for o in obras}
listas = [
    {'id': nid('l'), 'nombre': G('Cine mudo en orden', 'Silent films in order'), 'descripcion': G('Para empezar con el cine de antes.', 'To get started with early cinema.'),
     'obras': [por['Nosferatu'], por['Metropolis'], por['The General'], por['Sherlock Jr.']], 'numerada': True, 'cambio': ahora},
    {'id': nid('l'), 'nombre': G('Para un domingo', 'For a Sunday'), 'obras': [por['María'], por['Frankenstein']], 'cambio': ahora - 1000},
]
estado = {
    'v': 1, 'listo': True,
    'ajustes': {'nombre': 'Caro' if es else 'Sam', 'favoritas': [por['The General'], por['Nosferatu'], por['Frankenstein'], obras[15]['id']],
                'retos': {'libro': 12, 'pelicula': 52}, 'cambio': ahora},
    'obras': obras, 'artistas': [
        {'id': nid('a'), 'nombre': 'Los del Puerto', 'pais': 'CO', 'desde': 2009, 'tipo': 'Group', 'nota': 9, 'favorito': True, 'cambio': ahora},
        {'id': nid('a'), 'nombre': 'Nadia Sol', 'pais': 'CO', 'desde': 2016, 'tipo': 'Person', 'nota': 8, 'cambio': ahora},
    ],
    'listas': listas,
}
# Los álbumes, con su artista.
for o in obras:
    if o['tipo'] == 'album':
        for a in estado['artistas']:
            if a['nombre'] == o['creador']:
                o['artista'] = a['id']
json.dump(estado, open(salida, 'w'), ensure_ascii=False, indent=1)
