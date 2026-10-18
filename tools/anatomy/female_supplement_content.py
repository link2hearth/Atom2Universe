"""Original, explicitly illustrative female perineal teaching surfaces.

No third-party model is copied, traced or morphed. Mathematical sweeps and folds
are sized using published anatomical measurements and placed relative to HRA
landmarks. Base atlas vertices/faces are only read, never modified.
"""
import hashlib
import json
from pathlib import Path

import numpy as np

REGISTRATION = Path(__file__).with_name('female_supplement_registration.json')
REFERENCES = [
    'https://pubmed.ncbi.nlm.nih.gov/31254525/',
    'https://pubmed.ncbi.nlm.nih.gov/15842291/',
    'https://pmc.ncbi.nlm.nih.gov/articles/PMC1283096/',
    'https://pmc.ncbi.nlm.nih.gov/articles/PMC8376757/',
    'https://pmc.ncbi.nlm.nih.gov/articles/PMC12003430/',
    'https://training.seer.cancer.gov/anatomy/reproductive/female/genitalia.html',
]


def bezier(control, steps):
    p = np.asarray(control, dtype=np.float64)
    assert p.shape == (4, 3)
    t = np.linspace(0, 1, steps)
    points = ((1-t)**3)[:, None]*p[0] + (3*(1-t)**2*t)[:, None]*p[1] + (3*(1-t)*t*t)[:, None]*p[2] + (t**3)[:, None]*p[3]
    return points


def frames(points):
    tangent = np.gradient(points, axis=0)
    tangent /= np.linalg.norm(tangent, axis=1)[:, None]
    # Parallel transport avoids rotating an elliptic cross-section arbitrarily
    # at inflections. Initial transverse direction is the anatomical X axis.
    normals = []
    normal = np.array([1., 0., 0.])
    for axis in tangent:
        normal = normal - np.dot(normal, axis)*axis
        assert np.linalg.norm(normal) > .05
        normal /= np.linalg.norm(normal)
        normals.append(normal.copy())
    normals = np.array(normals)
    return normals, np.cross(tangent, normals)


def orient(vertices, faces):
    v = np.asarray(vertices, dtype=np.float64)
    f = np.asarray(faces, dtype=np.uint32)
    tri = v[f]
    signed = np.einsum('ij,ij->i', tri[:, 0], np.cross(tri[:, 1], tri[:, 2])).sum()/6
    if signed < 0:
        f = f[:, ::-1].copy()
    return v, f


def sweep(control, rx, ry, steps=64, sides=32, inner=None):
    """Elliptic curved wall; open lumen at each end when inner is supplied."""
    points = bezier(control, steps)
    nx, ny = frames(points)
    t = np.linspace(0, 1, steps)
    rx = np.broadcast_to(np.asarray(rx, dtype=float), (steps,))
    ry = np.broadcast_to(np.asarray(ry, dtype=float), (steps,))
    angles = np.arange(sides)*2*np.pi/sides
    vertices = points[:, None, :] + nx[:, None, :]*(rx[:, None]*np.cos(angles))[..., None] + ny[:, None, :]*(ry[:, None]*np.sin(angles))[..., None]
    vertices = vertices.reshape(-1, 3).tolist()
    faces = []

    def shell(offset, reverse=False):
        for i in range(steps-1):
            for j in range(sides):
                a = offset+i*sides+j
                b = offset+i*sides+(j+1)%sides
                c = a+sides
                d = b+sides
                for face in ((a, b, c), (b, d, c)):
                    faces.append(face[::-1] if reverse else face)

    shell(0)
    if inner is None:
        for i in (0, steps-1):
            center = len(vertices)
            vertices.append(points[i].tolist())
            for j in range(sides):
                a, b = i*sides+j, i*sides+(j+1)%sides
                faces.append((center, b, a) if i == 0 else (center, a, b))
    else:
        irx, iry = inner
        offset = len(vertices)
        inside = points[:, None, :] + nx[:, None, :]*(irx*np.cos(angles))[None, :, None] + ny[:, None, :]*(iry*np.sin(angles))[None, :, None]
        vertices.extend(inside.reshape(-1, 3).tolist())
        shell(offset, reverse=True)
        for i in (0, steps-1):
            for j in range(sides):
                a, b = i*sides+j, i*sides+(j+1)%sides
                c, d = a+offset, b+offset
                for face in ((a, c, b), (b, c, d)):
                    faces.append(face if i == 0 else face[::-1])
    return orient(vertices, faces)


def ovoid(center, lengths, axis=(0., -.95, .31), rings=30, sides=40):
    axis = np.asarray(axis, dtype=float)
    axis /= np.linalg.norm(axis)
    lateral = np.array([1., 0., 0.])
    depth = np.cross(axis, lateral)
    # Use finite terminal rings and caps, avoiding duplicate pole vertices.
    p = np.asarray(center) + np.linspace(-lengths[0]/2, lengths[0]/2, rings)[:, None]*axis
    t = np.linspace(-1, 1, rings)
    profile = np.sqrt(np.maximum(1-t*t, .0025))
    theta = np.arange(sides)*2*np.pi/sides
    v = p[:, None, :] + lateral[None, None, :]*(profile[:, None]*np.cos(theta)*lengths[1]/2)[..., None] + depth[None, None, :]*(profile[:, None]*np.sin(theta)*lengths[2]/2)[..., None]
    v = v.reshape(-1, 3).tolist()
    f = []
    for i in range(rings-1):
        for j in range(sides):
            a, b = i*sides+j, i*sides+(j+1)%sides
            f.extend([(a, b, a+sides), (b, b+sides, a+sides)])
    for i in (0, rings-1):
        k = len(v); v.append(p[i].tolist())
        for j in range(sides):
            a, b = i*sides+j, i*sides+(j+1)%sides
            f.append((k, b, a) if i == 0 else (k, a, b))
    return orient(v, f)


def labial_fold(control, side, width, thickness, phase, steps=76, across=12):
    """Thin, gently folded double-sided sheet with a sealed free margin."""
    root = bezier(control, steps)
    t = np.linspace(0, 1, steps)
    s = np.linspace(0, 1, across)
    fullness = .06 + .94*np.sin(np.pi*t)
    outward = np.array([0., -.95, .31])
    inward = np.array([-side, 0., 0.])
    free = .58*inward + .815*outward
    sheet = root[:, None, :] + fullness[:, None, None]*s[None, :, None]*width*free
    # Low-amplitude waves vary only the margin; never assert a unique texture.
    wave = .0008*np.sin(5*np.pi*t+phase)*np.sin(np.pi*t)
    sheet += wave[:, None, None]*s[None, :, None]**2*outward
    sheet += (.002*np.sin(np.pi*s))[None, :, None]*fullness[:, None, None]*outward
    du = np.gradient(sheet, axis=0)
    dv = np.gradient(sheet, axis=1)
    normal = np.cross(du, dv)
    normal /= np.linalg.norm(normal, axis=2)[..., None]
    local_thickness=thickness*(.15+.85*fullness)
    layers = [sheet+sign*normal*(local_thickness/2)[:,None,None] for sign in (-1, 1)]
    v = np.concatenate([p.reshape(-1, 3) for p in layers])
    size = steps*across
    f = []
    for i in range(steps-1):
        for j in range(across-1):
            a = i*across+j; b=a+1; c=a+across; d=c+1
            f.extend([(a,c,b),(b,c,d),(a+size,b+size,c+size),(b+size,d+size,c+size)])
    border = [*range(across), *[i*across+across-1 for i in range(1,steps)],
              *[(steps-1)*across+j for j in range(across-2,-1,-1)], *[i*across for i in range(steps-2,0,-1)]]
    for a,b in zip(border,border[1:]+border[:1]):
        f.extend([(a,b,a+size),(b,b+size,a+size)])
    return orient(v, f)


def build(source, base_items):
    registration = json.loads(REGISTRATION.read_text(encoding='utf8'))
    # Require the expected base atlas; no gender swap or geometric mutation.
    base = {r['id']:(r,v,f) for r,v,f in base_items}
    assert {'HRAF_2','HRAF_431','HRAF_665','HRAF_967','HRAF_968'} <= base.keys()
    assert all(base[k][0]['source']=='hra-female-v1.5' for k in ('HRAF_2','HRAF_431','HRAF_665'))
    curves = registration['curves']; dims = registration['dimensions']
    shift = np.array(registration['translation'])
    items = []
    metrics = {}

    intro_en = 'Illustrative anatomical reconstruction, not a segmented donor structure. '
    intro_fr = 'Reconstitution anatomique illustrative, et non structure segmentée du donneur. '

    def add(key, en, fr, geometry, text_en, text_fr, color, wiki_en, wiki_fr, urinary=False):
        v,f = geometry
        assert np.isfinite(v).all() and f.min()>=0 and f.max()<len(v)
        identifier='A2U_FEMALE_'+key.upper()
        record=dict(id=identifier,sourceName='reconstructed_'+key,sourceObject=key,
                    source='atom2universe-reconstruction',reconstructed=True,
                    elements=[identifier],nameEn=en,nameFr=fr,
                    summaryEn=intro_en+text_en,summaryFr=intro_fr+text_fr,
                    layer='organs',region='pelvis',organSystem='urinary' if urinary else 'reproductive',
                    category='female_reconstructed_urethra' if urinary else 'female_reconstructed_vulva',
                    groupEn='Urethra · reconstruction' if urinary else 'Vulva and clitoris · reconstructions',
                    groupFr='Urètre · reconstitution' if urinary else 'Vulve et clitoris · reconstitutions',
                    wikiEn=wiki_en,wikiFr=wiki_fr,color=color,standard=False,boneCount=0)
        items.append((record,v+shift,f))
        metrics[key]=dict(vertices=len(v),triangles=len(f),nativeMin=v.min(0).tolist(),nativeMax=v.max(0).tolist())

    add('clitoral_glans','Clitoral glans','Gland du clitoris',
        ovoid(registration['anchors']['clitoralGlansCentre'],[dims['glansLengthMm']/1000,dims['glansWidthMm']/1000,dims['glansDepthMm']/1000]),
        'The glans is the sensitive distal part of the clitoris. Its size and shape vary between individuals. The clitoral hood that partly covers it is not represented here.',
        'Le gland est la partie distale sensible du clitoris. Sa taille et sa forme varient entre les individus. Le capuchon clitoridien qui le recouvre partiellement n’est pas représenté ici.',
        [.83,.43,.47],'Clitoris','Clitoris')
    t=np.linspace(0,1,64)
    profile=(.90+.10*np.sin(np.pi*t))*(1-.50*t**5)
    add('clitoral_body','Body of clitoris','Corps du clitoris',sweep(curves['clitoral_body'],.0045*profile,.003*profile),
        'The paired corpora cavernosa form the body and continue into the two crura. The curved course is placed below the pubic arch; it is a teaching approximation of these relations.',
        'Les deux corps caverneux forment le corps et se prolongent dans les deux piliers. Le trajet courbe est placé sous l’arcade pubienne ; il illustre ces rapports de façon approximative.',
        [.70,.27,.36],'Clitoris','Clitoris')
    for key,side,frside in [('crus_left','Left','gauche'),('crus_right','Right','droit')]:
        t=np.linspace(0,1,64);profile=.15+.85*np.sin(np.pi*t/2)**.6
        add(key,f'{side} crus of clitoris',f'Pilier {frside} du clitoris',sweep(curves[key],.0038*profile,.0032*profile),
            'This root continues the corresponding corpus cavernosum along the ischiopubic ramus. The reconstructed attachment follows the bony margin without representing its microscopic connective tissues.',
            'Ce pilier prolonge le corps caverneux correspondant le long de la branche ischio-pubienne. Son attache reconstruite suit le bord osseux sans représenter les tissus conjonctifs microscopiques.',
            [.69,.30,.40],'Crus_of_clitoris','Clitoris')
    for key,side,frside in [('bulb_left','Left','gauche'),('bulb_right','Right','droit')]:
        t=np.linspace(0,1,64);profile=.07+.93*np.sin(np.pi*t)**.72
        add(key,f'{side} vestibular bulb',f'Bulbe vestibulaire {frside}',sweep(curves[key],.0046*profile,.0038*profile),
            'The vestibular bulbs are paired erectile tissues beside the vaginal vestibule. They are distinct from the clitoral crura and normally lie deep to bulbospongiosus muscles, which are not reconstructed here.',
            'Les bulbes vestibulaires sont des tissus érectiles pairs situés de part et d’autre du vestibule vaginal. Ils se distinguent des piliers clitoridiens et se trouvent normalement sous les muscles bulbo-spongieux, non reconstitués ici.',
            [.74,.34,.48],'Bulb_of_vestibule','Bulbe_du_vestibule')
    for key,side,frside in [('labium_majus_left','Left','gauche'),('labium_majus_right','Right','droite')]:
        t=np.linspace(0,1,64);profile=.045+.955*np.sin(np.pi*t)**.70
        add(key,f'{side} labium majus',f'Grande lèvre {frside}',sweep(curves[key],.0075*profile,.0065*profile),
            'The labia majora are outer skin folds containing adipose tissue. This smooth reconstructed fold illustrates one possible morphology; the HRA skin is retained as a separate surface and can cover it.',
            'Les grandes lèvres sont des replis cutanés externes contenant du tissu adipeux. Ce repli lisse reconstitué illustre une morphologie possible ; la peau HRA est conservée comme surface distincte et peut le recouvrir.',
            [.73,.45,.37],'Labia_majora','Grande_lèvre')
    for key,side,frside,sign,phase in [('labium_minus_left','Left','gauche',1,.4),('labium_minus_right','Right','droite',-1,1.1)]:
        add(key,f'{side} labium minus',f'Petite lèvre {frside}',labial_fold(curves[key],sign,.015,.0012,phase),
            'The labia minora are thin folds bordering the vestibule. Their free margins are highly variable and often asymmetric. The urethral and vaginal openings are separate; this supplement does not open the capped native HRA vagina.',
            'Les petites lèvres sont de fins replis bordant le vestibule. Leurs bords libres sont très variables et souvent asymétriques. Les orifices urétral et vaginal sont distincts ; ce complément n’ouvre pas le vagin HRA natif fermé.',
            [.79,.43,.43],'Labia_minora','Petite_lèvre')
    t=np.linspace(0,1,80)
    transition=1-np.exp(-(t/.23)**2)
    outerx=.0020+.0014*transition-.0010*t*t
    outery=.00135+.00145*transition-.0008*t*t
    add('female_urethra','Female urethra','Urètre féminin',sweep(curves['urethra'],outerx,outery,steps=80,sides=36,inner=(.0011,.0006)),
        'The urethra passes from the bladder neck to its own meatus anterior to the vaginal opening. The lumen is shown open for study; its walls can normally appose. The native HRA neck remains capped, so this is not a continuous fluid model of the bladder.',
        'L’urètre relie le col vésical à son propre méat, en avant de l’orifice vaginal. La lumière est montrée ouverte pour l’étude ; ses parois peuvent normalement s’accoler. Le col HRA natif reste fermé : il ne s’agit pas d’un modèle fluidique continu de la vessie.',
        [.78,.63,.46],'Female_urethra','Urètre',urinary=True)

    for key,control in curves.items():
        points=bezier(control,512)
        metrics.setdefault(key,{})['centrelineLengthMm']=float(np.linalg.norm(np.diff(points,axis=0),axis=1).sum()*1000)
    report=dict(source='Original mathematical teaching reconstruction for Atom2Universe',reconstructed=True,
                sourceGeometry='No third-party genital mesh copied, traced, registered or modified.',
                referenceData='Published measurements and anatomical relationships guide an illustrative example, not a patient-specific or average shape.',
                references=REFERENCES,registration=registration,
                registrationSha256=hashlib.sha256(REGISTRATION.read_bytes()).hexdigest(),
                generatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                renderedStructures=len(items),triangles=sum(len(f) for _,_,f in items),metrics=metrics,
                sourceSearch={'FillodCosentino':'Official source licence CC BY-NC-SA: excluded, including conflicting CC0/CC BY mirrors.',
                              'UNIGE':'3D kits not found as meshes with a verified freely redistributable licence; the CC BY-SA statement in the 2020 paper concerns 2D drawings.',
                              'Wu2020':'Interactive pelvic-floor PDF is advertised, but mesh reuse licence and downloadable geometry were not verified.',
                              'Kiesel2022':'Article open access; STL files available only by author request, no downloadable licensed mesh identified.'})
    return items,report
