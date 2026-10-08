"""BodyParts3D external skin and hair, hidden by default in the application."""
TERMS = {
    'FMA7163': ('skin', 'Peau', 'FJ2810', 'skin_surface'),
    'FMA54241': ('hair of head', 'Cheveux', 'FJ2813', 'skin_hair'),
    'FMA54319': ('pubic hair', 'Poils pubiens', 'FJ2815', 'skin_hair'),
}
BY_NAME = {r[0]: r[1:] for r in TERMS.values()}
groups = {'skin_surface': ('Skin surface', 'Surface cutanée'), 'skin_hair': ('Hair', 'Pilosité')}
summaries = {
    'skin_surface': ('External body envelope. Skin provides a protective barrier, contributes to temperature regulation and contains sensory receptors. This mesh shows the external surface of the source adult male, without separate epidermis, dermis, glands or microscopic structures.', 'Enveloppe externe du corps. La peau forme une barrière protectrice, participe à la thermorégulation et contient des récepteurs sensoriels. Ce maillage représente la surface externe du sujet masculin adulte de la source, sans épiderme, derme, glandes ni structures microscopiques séparés.'),
    'skin_hair': ('Hair is formed by keratinised cells produced in follicles within the skin. The mesh is a grouped surface representation of the source hair distribution, not a model of individual hairs or follicles.', 'Les poils sont formés de cellules kératinisées produites dans des follicules cutanés. Le maillage est une représentation groupée de la pilosité de la source, sans poils ni follicules individualisés.'),
}
references = ['https://openstax.org/books/anatomy-and-physiology-2e/pages/5-1-layers-of-the-skin',
              'https://openstax.org/books/anatomy-and-physiology-2e/pages/5-2-accessory-structures-of-the-skin']

def entries(concepts, partof):
    result = []
    for identity, (name, french, element, kind) in TERMS.items():
        assert {r['element file id'] for r in concepts[identity]} == {element}, identity
        assert {r['name'] for r in concepts[identity]} == {name}, (identity, name)
        result.append((identity, name, [element], False, 0))
    return result

def describe(name):
    french, element, kind = BY_NAME[name]
    region = 'body' if kind == 'skin_surface' else 'skull' if name == 'hair of head' else 'pelvis'
    return name.capitalize(), french, kind, region, ('Skin' if kind == 'skin_surface' else 'Hair'), ('Peau' if kind == 'skin_surface' else 'Poil')

def attributes(name):
    kind = BY_NAME[name][2]
    return {'category': kind, 'color': [.73, .53, .40] if kind == 'skin_surface' else [.12, .08, .06]}

def clean_geometry(element, vertices, faces):
    if element == 'FJ2810':
        from skin_registration import adapt_skin
        return adapt_skin(vertices, faces)
    return vertices, faces

def provenance(source, entries):
    from skin_registration import report
    return {'source': 'BodyParts3D 4.0', 'structures': len(entries),
            'modifications': 'Skin topology and indices retained. Local outward accommodation of the outer skin surface covers confirmed protrusions of the iliotibial tracts, medial gastrocnemius heads, sartorius muscles and anterior-neck platysma. The inner surface is retained, with approximately 0.5 mm of target cover and a smooth 20 mm transition around supporting face corners. Displacements are bounded at 8 mm around the iliotibial tracts and 12 mm in the additional regions. Original and displayed muscle geometry, hair and all other anatomical meshes are unchanged. Illustrative uniform material colours; layer initially hidden.',
            'skinAccommodation': report(),
            'limitations': 'Adult male surface only. The local adjustments are illustrative accommodations between source surfaces, not measured skin or subcutaneous-tissue thickness. No separately modelled skin microanatomy, nails or individual follicles. Skin is one whole-body mesh. Coverage of the targeted structures does not establish whole-body clinical validation.'}
