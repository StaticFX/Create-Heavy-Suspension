use std::alloc::{alloc as rust_alloc, dealloc as rust_dealloc, Layout};
use glam::{DMat3, DVec2, DVec3};
use crate::sable::MassData;
use crate::suspension::suspension_for_normal_mass;
use crate::solver::{calculate_impulses, BodyVelocity, SolverWheel};
use crate::wheels::{calculate_load, Wheel};

const ABI_VERSION: u32 = 4;
const ALIGN: usize = 8;

#[unsafe(no_mangle)]
pub extern "C" fn abi_version() -> u32 {
    ABI_VERSION
}

#[unsafe(no_mangle)]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    if size == 0 { return std::ptr::null_mut(); }
    unsafe { rust_alloc(Layout::from_size_align(size, ALIGN).unwrap()) }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn dealloc(ptr: *mut u8, size: usize) {
    if size == 0 || ptr.is_null() { return; }
    unsafe { rust_dealloc(ptr, Layout::from_size_align(size, ALIGN).unwrap()); }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn suspension(
    total_mass: f64,
    normal_mass: f64,
    load_fraction: f64,
    multiplier: f64,
    wheel_count: u32,
    touching_count: u32,
    dt: f64,
    out: *mut f64,
) -> u32 {
    let Some(suspension) = suspension_for_normal_mass(
        total_mass, normal_mass, load_fraction, multiplier, wheel_count as usize, touching_count as usize, dt,
    ) else {
        return 0;
    };

    let out = unsafe { std::slice::from_raw_parts_mut(out, 3) };
    out.copy_from_slice(&[suspension.springiness, suspension.heave_damping, suspension.rotation_damping]);
    1
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn calc_distribution(mass: *const f64, wheels: *const f64, wheel_count: u32, fractions_out: *mut f64) -> u32 {
    let count = wheel_count as usize;
    if count == 0 || mass.is_null() || wheels.is_null() || fractions_out.is_null() {
        return 0;
    }

    let mass = unsafe { std::slice::from_raw_parts(mass, 14) };
    let wheels = unsafe { std::slice::from_raw_parts(wheels, count * 4) };
    let fractions_out = unsafe { std::slice::from_raw_parts_mut(fractions_out, count) };


    let mass_data = read_mass(mass);
    let wheels = read_wheels(&wheels);

    let center_of_mass = DVec2::new(mass_data.center_of_mass.x, mass_data.center_of_mass.z);
    let fractions = calculate_load(&wheels, center_of_mass);
    if fractions.iter().any(|fraction| !fraction.is_finite()) {
        return 0;
    }

    fractions_out.copy_from_slice(&fractions);
    1
}


fn read_mass(data: &[f64]) -> MassData {
    MassData {
        total_mass: data[0],
        inverse_mass: data[1],
        center_of_mass: DVec3::new(data[2], data[3], data[4]),
        inverse_inertia: DMat3::from_cols_slice(&data[5..14])
    }
}

fn read_wheels(data: &[f64]) -> Vec<Wheel> {
    data.chunks_exact(4)
        .map(|wheel| Wheel {
            touch_point: DVec3::new(wheel[0], wheel[1], wheel[2]),
            stiffness_multiplier: wheel[3],
        })
        .collect()
}

const SOLVER_VALUES_PER_WHEEL: usize = 7;
const MOTION_VALUES: usize = 9;

#[unsafe(no_mangle)]
pub unsafe extern "C" fn solve_vehicle(
    mass: *const f64,
    wheels: *const f64,
    wheel_count: u32,
    motion: *const f64,
    dt: f64,
    impulses_out: *mut f64,
) -> u32 {
    let count = wheel_count as usize;
    if count == 0 || mass.is_null() || wheels.is_null() || motion.is_null() || impulses_out.is_null() { return 0; }

    let mass = unsafe { std::slice::from_raw_parts(mass, 14) };
    let wheels = unsafe { std::slice::from_raw_parts(wheels, count * SOLVER_VALUES_PER_WHEEL) };
    let motion = unsafe { std::slice::from_raw_parts(motion, MOTION_VALUES) };
    let impulses_out = unsafe { std::slice::from_raw_parts_mut(impulses_out, count) };

    let mass_data = read_mass(mass);
    let wheels = read_solver_wheels(wheels);
    let velocity = read_velocity(motion);
    let gravity = read_gravity(motion);

    let Some(impulses) = calculate_impulses(&mass_data, &wheels, &velocity, gravity, dt) else { return 0; };
    if impulses.iter().any(|impulse| !impulse.is_finite()) { return 0; }

    impulses_out.copy_from_slice(&impulses);
    1
}

fn read_solver_wheels(data: &[f64]) -> Vec<SolverWheel> {
    data.chunks_exact(SOLVER_VALUES_PER_WHEEL)
        .map(|wheel| SolverWheel {
            contact: DVec3::new(wheel[0], wheel[1], wheel[2]),
            multiplier: wheel[3],
            load_fraction: wheel[4],
            compression: wheel[5],
            age_in_steps: wheel[6],
        })
        .collect()
}

fn read_velocity(data: &[f64]) -> BodyVelocity {
    BodyVelocity {
        linear: DVec3::new(data[0], data[1], data[2]),
        angular: DVec3::new(data[3], data[4], data[5]),
    }
}

fn read_gravity(data: &[f64]) -> DVec3 {
    DVec3::new(data[6], data[7], data[8])
}
