"""Generate Minecraft schematic test shapes for the LatticeStressSolver test battery.

Usage:
    python generate_test_shapes.py          # generate all shapes
    python generate_test_shapes.py --list    # list all shapes
    python generate_test_shapes.py boxes     # generate only boxes
    python generate_test_shapes.py --sizes 5 10 15  # custom sizes

Output: .schem files in test_shapes/ directory
"""

from mcschematic import MCSchematic, Version
import os
import argparse
import math
import itertools

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
MC_VERSION = Version.JE_1_21_1

MATERIALS = {
    "oak": "minecraft:oak_planks",
    "iron": "minecraft:iron_block",
    "diamond": "minecraft:diamond_block",
    "stone": "minecraft:stone",
    "glass": "minecraft:glass",
    "netherite": "minecraft:netherite_block",
}


def make_rect_prism(sx, sy, sz, mat, hollow=False, thickness=1):
    """Generate a rectangular prism (solid or hollow with ribs)."""
    schem = MCSchematic()
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                place = True
                if hollow:
                    dx = min(x, sx - 1 - x)
                    dy = min(y, sy - 1 - y)
                    dz = min(z, sz - 1 - z)
                    if min(dx, dy, dz) >= thickness:
                        place = False
                if place:
                    schem.setBlock((x, y, z), mat)
    return schem, schem


def make_ribbed_box(sx, sy, sz, mat, thickness=1):
    """Box with structural ribs every 4 blocks."""
    schem = MCSchematic()
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                dx = min(x, sx - 1 - x)
                dy = min(y, sy - 1 - y)
                dz = min(z, sz - 1 - z)
                if min(dx, dy, dz) < thickness:
                    schem.setBlock((x, y, z), mat)
                elif x % 4 == 0 and z % 4 == 0:
                    schem.setBlock((x, y, z), mat)
    return schem, schem


def make_sphere(diameter, mat):
    """Generate a sphere (voxelized)."""
    r = diameter / 2.0
    cx = cy = cz = r - 0.5
    schem = MCSchematic()
    for x in range(diameter):
        for y in range(diameter):
            for z in range(diameter):
                dx = x - cx
                dy = y - cy
                dz = z - cz
                if dx * dx + dy * dy + dz * dz <= r * r:
                    schem.setBlock((x, y, z), mat)
    return schem, schem


def make_ellipsoid(dx_scale, dy_scale, dz_scale, mat, name):
    """Generate an ellipsoid: x/dx_scale^2 + y/dy_scale^2 + z/dz_scale^2 <= 1."""
    size_x = int(2 * dx_scale) + 1
    size_y = int(2 * dy_scale) + 1
    size_z = int(2 * dz_scale) + 1
    cx = (size_x - 1) / 2.0
    cy = (size_y - 1) / 2.0
    cz = (size_z - 1) / 2.0
    schem = MCSchematic()
    for x in range(size_x):
        for y in range(size_y):
            for z in range(size_z):
                dx = (x - cx) / dx_scale
                dy = (y - cy) / dy_scale
                dz = (z - cz) / dz_scale
                if dx * dx + dy * dy + dz * dz <= 1.0:
                    schem.setBlock((x, y, z), mat)
    return schem, schem


def make_cylinder(radius, height, mat):
    """Generate a cylinder oriented along Y."""
    schem = MCSchematic()
    r2 = radius * radius
    for x in range(-radius, radius + 1):
        for z in range(-radius, radius + 1):
            if x * x + z * z <= r2:
                for y in range(height):
                    schem.setBlock((x + radius, y, z + radius), mat)
    return schem, schem


def make_cone(radius, height, mat):
    """Generate a cone oriented along Y."""
    schem = MCSchematic()
    for y in range(height):
        r = int(radius * (1 - y / height))
        r2 = r * r
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                if x * x + z * z <= r2:
                    schem.setBlock((x + radius, y, z + radius), mat)
    return schem, schem


def make_torus(major_r, minor_r, mat):
    """Generate a torus (major radius, minor radius)."""
    size = int(2 * (major_r + minor_r)) + 1
    cx = cy = cz = (size - 1) / 2.0
    schem = MCSchematic()
    for x in range(size):
        for y in range(size):
            for z in range(size):
                dx = x - cx
                dy = y - cy
                dz = z - cz
                d_radial = math.sqrt(dx * dx + dz * dz) - major_r
                if d_radial * d_radial + dy * dy <= minor_r * minor_r:
                    schem.setBlock((x, y, z), mat)
    return schem, schem


def generate_all(args):
    """Generate all test shape schematics."""
    os.makedirs(OUT_DIR, exist_ok=True)

    sizes = args.sizes if args.sizes else [10]
    thicknesses = args.thicknesses if args.thicknesses else [1, 2, 3]
    mats = args.materials if args.materials else list(MATERIALS.keys())

    generated = []

    # Boxes: solid, hollow, ribbed
    for mat_name in mats:
        mat = MATERIALS[mat_name]
        for size in sizes:
            # Solid box
            name = f"box_{mat_name}_{size}_solid"
            schem, _ = make_rect_prism(size, size, size, mat, hollow=False)
            schem.save(OUT_DIR, name, MC_VERSION)
            generated.append(name)

            # Hollow box with varying thickness
            for t in thicknesses:
                if t < size // 2:
                    name = f"box_{mat_name}_{size}_hollow_t{t}"
                    schem, _ = make_rect_prism(size, size, size, mat, hollow=True, thickness=t)
                    schem.save(OUT_DIR, name, MC_VERSION)
                    generated.append(name)

            # Ribbed box
            for t in thicknesses:
                if t < size // 2:
                    name = f"box_{mat_name}_{size}_ribbed_t{t}"
                    schem, _ = make_ribbed_box(size, size, size, mat, thickness=t)
                    schem.save(OUT_DIR, name, MC_VERSION)
                    generated.append(name)

    # Spheres
    for mat_name in mats:
        mat = MATERIALS[mat_name]
        for size in sizes:
            if size >= 3:
                name = f"sphere_{mat_name}_{size}"
                schem, _ = make_sphere(size, mat)
                schem.save(OUT_DIR, name, MC_VERSION)
                generated.append(name)

    # Ellipsoids (2:1:1, 1:2:1, 4:1:1)
    for mat_name in mats:
        mat = MATERIALS[mat_name]
        for radius in [r for r in sizes if r >= 5]:
            for scales, label in [
                ((radius, radius // 2, radius // 2), "211"),
                ((radius // 2, radius, radius // 2), "121"),
                ((radius, radius // 4, radius // 4), "411"),
            ]:
                if all(s >= 2 for s in scales):
                    name = f"ellipsoid_{label}_{mat_name}_{radius}"
                    schem, _ = make_ellipsoid(*scales, mat, name)
                    schem.save(OUT_DIR, name, MC_VERSION)
                    generated.append(name)

    # Cylinders
    for mat_name in mats:
        mat = MATERIALS[mat_name]
        for size in sizes:
            if size >= 3:
                name = f"cylinder_{mat_name}_{size}"
                schem, _ = make_cylinder(size // 2, size, mat)
                schem.save(OUT_DIR, name, MC_VERSION)
                generated.append(name)

    # Cones (iron only for novelty)
    for size in sizes:
        if size >= 3:
            name = f"cone_iron_{size}"
            schem, _ = make_cone(size // 2, size, MATERIALS["iron"])
            schem.save(OUT_DIR, name, MC_VERSION)
            generated.append(name)

    # Torus (iron only, large sizes only)
    for size in [s for s in sizes if s >= 7]:
        name = f"torus_iron_{size}"
        schem, _ = make_torus(size / 2, size / 6, MATERIALS["iron"])
        schem.save(OUT_DIR, name, MC_VERSION)
        generated.append(name)

    # Print summary
    print(f"Generated {len(generated)} schematics in {OUT_DIR}/")
    for g in sorted(generated):
        print(f"  {g}.schem")
    print(f"\nTo test: /submarine testbattery schematic <path>")
    print(f"Or generate CSV: /submarine testbattery csv test_battery.csv")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate Minecraft schematic test shapes")
    parser.add_argument("--sizes", type=int, nargs="+", default=[10, 16],
                        help="Sizes to generate (default: 10 16)")
    parser.add_argument("--thicknesses", type=int, nargs="+", default=[1, 2],
                        help="Wall thicknesses for hollow shapes (default: 1 2)")
    parser.add_argument("--materials", type=str, nargs="+",
                        default=["oak", "iron", "diamond"],
                        help="Materials to use (default: oak iron diamond)")
    parser.add_argument("--list", action="store_true",
                        help="List all shapes that would be generated")

    args = parser.parse_args()
    generate_all(args)
