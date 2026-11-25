using AstroExplorer.Models;

namespace AstroExplorer.Services;

/// <summary>
/// Built-in reference information about popular celestial objects for beginners.
/// Stored in code so it works offline. Figures are approximate and given for
/// observers in the northern hemisphere (e.g. the UK).
/// </summary>
public class CatalogueService
{
    public IReadOnlyList<CelestialObjectInfo> All { get; } =
    [
        new()
        {
            Name = "Moon", Type = "Moon", Symbol = "☾", Constellation = "Moves through the zodiac",
            Distance = "About 384,400 km", Brightness = "Up to magnitude −12.7 (full Moon)",
            BestSeen = "Any clear night; best a few days either side of first quarter",
            Description = "Earth's only natural satellite and the easiest target in the sky. Its surface shows dark lava plains (maria), bright highlands and thousands of craters.",
            ObservingTips = "Look along the terminator, the line between light and dark, where shadows make craters and mountains stand out. A full Moon is very bright and flat-looking."
        },
        new()
        {
            Name = "Mercury", Type = "Planet", Symbol = "☿", Constellation = "Close to the Sun",
            Distance = "About 77–222 million km from Earth", Brightness = "Up to about magnitude −2",
            BestSeen = "Low in twilight around greatest elongation",
            Description = "The smallest planet and the closest to the Sun. It never strays far from the Sun, so it is only seen briefly after sunset or before sunrise.",
            ObservingTips = "Find a flat, clear horizon. Never sweep binoculars near the Sun while it is above the horizon."
        },
        new()
        {
            Name = "Venus", Type = "Planet", Symbol = "♀", Constellation = "Close to the Sun",
            Distance = "About 38–261 million km from Earth", Brightness = "Up to about magnitude −4.9",
            BestSeen = "Evening or morning 'star', depending on its orbit",
            Description = "The brightest planet, wrapped in thick clouds. It is often mistaken for an aircraft or UFO because it is so bright.",
            ObservingTips = "A small telescope shows its phases, like a tiny Moon. Observe in twilight to reduce glare."
        },
        new()
        {
            Name = "Mars", Type = "Planet", Symbol = "♂", Constellation = "Moves through the zodiac",
            Distance = "About 55–400 million km from Earth", Brightness = "Up to about magnitude −2.9 at opposition",
            BestSeen = "Around opposition, which happens about every 26 months",
            Description = "The red planet. Its colour comes from iron oxide dust. Near opposition, telescopes show polar ice caps and dark surface markings.",
            ObservingTips = "Use higher magnification on nights of steady seeing. Its size changes a lot between oppositions."
        },
        new()
        {
            Name = "Jupiter", Type = "Planet", Symbol = "♃", Constellation = "Moves through the zodiac",
            Distance = "About 590–970 million km from Earth", Brightness = "Up to about magnitude −2.9",
            BestSeen = "Months around opposition (roughly once a year)",
            Description = "The largest planet. Even binoculars show its four big Galilean moons: Io, Europa, Ganymede and Callisto. Telescopes reveal cloud belts and the Great Red Spot.",
            ObservingTips = "Watch the moons change position over a few hours. Brace binoculars on something steady."
        },
        new()
        {
            Name = "Saturn", Type = "Planet", Symbol = "♄", Constellation = "Moves through the zodiac",
            Distance = "About 1.2–1.7 billion km from Earth", Brightness = "About magnitude 0 to +1",
            BestSeen = "Months around opposition (roughly once a year)",
            Description = "The ringed planet. Its rings are made of ice and rock. The tilt of the rings changes over its 29-year orbit; they appeared edge-on in 2025 and are opening up again.",
            ObservingTips = "Any small telescope at 30× or more shows the rings. Look for its largest moon, Titan, nearby."
        },
        new()
        {
            Name = "Uranus", Type = "Planet", Symbol = "⛢", Constellation = "Moves slowly through the zodiac",
            Distance = "About 2.6–3.2 billion km from Earth", Brightness = "About magnitude 5.7",
            BestSeen = "Autumn and winter evenings in the mid-2020s",
            Description = "An ice giant tipped on its side. It is right at the limit of naked-eye visibility under a very dark sky.",
            ObservingTips = "Use a star chart and binoculars. In a telescope it looks like a small, pale blue-green disc."
        },
        new()
        {
            Name = "Neptune", Type = "Planet", Symbol = "♆", Constellation = "Moves slowly through the zodiac",
            Distance = "About 4.3–4.7 billion km from Earth", Brightness = "About magnitude 7.8",
            BestSeen = "Autumn evenings",
            Description = "The most distant planet. It was found in 1846 by mathematical prediction before it was ever seen.",
            ObservingTips = "Needs binoculars or a telescope and a detailed finder chart. It looks like a faint bluish star."
        },
        new()
        {
            Name = "Sirius", Type = "Star", Symbol = "✦", Constellation = "Canis Major",
            Distance = "8.6 light years", Brightness = "Magnitude −1.46 (brightest star in the night sky)",
            BestSeen = "Winter evenings, low in the south",
            Description = "The 'Dog Star'. It is bright partly because it is very close to us. It has a faint white dwarf companion, Sirius B.",
            ObservingTips = "Close to the horizon it twinkles in many colours because of turbulence in our atmosphere."
        },
        new()
        {
            Name = "Betelgeuse", Type = "Star", Symbol = "✦", Constellation = "Orion",
            Distance = "Roughly 500–700 light years", Brightness = "Variable, about magnitude 0.0 to 1.6",
            BestSeen = "Winter evenings",
            Description = "A red supergiant marking Orion's shoulder. It is so large it would reach past the orbit of Mars if placed where our Sun is.",
            ObservingTips = "Compare its orange colour with blue-white Rigel at the opposite corner of Orion."
        },
        new()
        {
            Name = "Polaris", Type = "Star", Aliases = ["North Star", "Pole Star"], Symbol = "✦", Constellation = "Ursa Minor",
            Distance = "About 430 light years", Brightness = "About magnitude 2.0",
            BestSeen = "All year round from the northern hemisphere",
            Description = "The North Star sits almost exactly above Earth's north pole, so the rest of the sky appears to turn around it.",
            ObservingTips = "Follow the two 'pointer' stars at the end of the Plough (Big Dipper) to find it."
        },
        new()
        {
            Name = "Vega", Type = "Star", Symbol = "✦", Constellation = "Lyra",
            Distance = "About 25 light years", Brightness = "Magnitude 0.03",
            BestSeen = "Summer and autumn evenings, high overhead",
            Description = "A bright blue-white star and one corner of the Summer Triangle with Deneb and Altair.",
            ObservingTips = "Nearby Epsilon Lyrae, the 'Double Double', is a nice test for a small telescope."
        },
        new()
        {
            Name = "Albireo", Type = "Double star", Symbol = "✧", Constellation = "Cygnus",
            Distance = "About 400 light years", Brightness = "About magnitude 3.1 and 5.1",
            BestSeen = "Summer and autumn evenings",
            Description = "One of the most beautiful double stars: a golden-orange star next to a sapphire-blue companion.",
            ObservingTips = "The pair splits easily at low power in any small telescope."
        },
        new()
        {
            Name = "Pleiades", Type = "Star cluster", Aliases = ["M45", "Seven Sisters"], Symbol = "⁂", Constellation = "Taurus",
            Distance = "About 444 light years", Brightness = "Magnitude 1.6 overall",
            BestSeen = "Autumn and winter evenings",
            Description = "A young open cluster of hot blue stars, also called the Seven Sisters. Most people can see six or seven stars with the naked eye.",
            ObservingTips = "Binoculars give the best view; a telescope's field of view is often too narrow."
        },
        new()
        {
            Name = "Orion Nebula", Type = "Nebula", Aliases = ["M42"], Symbol = "☁", Constellation = "Orion",
            Distance = "About 1,340 light years", Brightness = "Magnitude 4.0",
            BestSeen = "Winter evenings",
            Description = "A huge cloud of gas and dust where new stars are being born. It is visible to the naked eye as the fuzzy 'star' in Orion's Sword.",
            ObservingTips = "A telescope shows the Trapezium, four young stars at its heart. Averted vision brings out more nebulosity."
        },
        new()
        {
            Name = "Ring Nebula", Type = "Nebula", Aliases = ["M57"], Symbol = "◯", Constellation = "Lyra",
            Distance = "About 2,300 light years", Brightness = "Magnitude 8.8",
            BestSeen = "Summer evenings",
            Description = "A planetary nebula: the glowing shell of gas thrown off by a dying Sun-like star.",
            ObservingTips = "Look between the two stars at the bottom of Lyra. It appears as a small, grey 'smoke ring' in a telescope."
        },
        new()
        {
            Name = "Andromeda Galaxy", Type = "Galaxy", Aliases = ["M31", "Andromeda"], Symbol = "◎", Constellation = "Andromeda",
            Distance = "About 2.5 million light years", Brightness = "Magnitude 3.4",
            BestSeen = "Autumn evenings",
            Description = "The nearest large galaxy to the Milky Way and the most distant object most people can see with the naked eye. It holds around a trillion stars.",
            ObservingTips = "From a dark site it looks like an elongated smudge. Binoculars show its bright core; look for companion galaxies M32 and M110."
        },
        new()
        {
            Name = "Hercules Cluster", Type = "Star cluster", Aliases = ["M13"], Symbol = "⁂", Constellation = "Hercules",
            Distance = "About 22,000–25,000 light years", Brightness = "Magnitude 5.8",
            BestSeen = "Late spring and summer evenings",
            Description = "A globular cluster of several hundred thousand ancient stars packed into a ball.",
            ObservingTips = "Binoculars show a fuzzy ball; a 150 mm telescope begins to resolve individual stars at the edges."
        },
        new()
        {
            Name = "Double Cluster", Type = "Star cluster", Aliases = ["NGC 869", "NGC 884"], Symbol = "⁂", Constellation = "Perseus",
            Distance = "About 7,500 light years", Brightness = "About magnitude 4 each",
            BestSeen = "Autumn and winter evenings",
            Description = "Two rich open clusters side by side between Perseus and Cassiopeia.",
            ObservingTips = "Use the lowest magnification you have so both clusters fit in the same view."
        },
        new()
        {
            Name = "International Space Station", Type = "Satellite / ISS", Aliases = ["ISS"], Symbol = "✈", Constellation = "Crosses the sky",
            Distance = "About 400 km above Earth", Brightness = "Often brighter than any star",
            BestSeen = "Passes shortly after dusk or before dawn",
            Description = "The largest human-made object in orbit. It circles Earth about every 90 minutes.",
            ObservingTips = "It looks like a bright, steady light moving smoothly with no flashing. Check pass times online and be outside a few minutes early."
        },
    ];

    public IEnumerable<CelestialObjectInfo> Search(string? query)
    {
        if (string.IsNullOrWhiteSpace(query)) return All;
        string q = query.Trim();
        return All.Where(o =>
            o.Name.Contains(q, StringComparison.OrdinalIgnoreCase) ||
            o.Type.Contains(q, StringComparison.OrdinalIgnoreCase) ||
            o.Constellation.Contains(q, StringComparison.OrdinalIgnoreCase) ||
            o.Aliases.Any(a => a.Contains(q, StringComparison.OrdinalIgnoreCase)));
    }

    /// <summary>Finds the catalogue entry an observation refers to, if any.</summary>
    public CelestialObjectInfo? FindFor(string objectName) => All.FirstOrDefault(o => o.Matches(objectName));
}
