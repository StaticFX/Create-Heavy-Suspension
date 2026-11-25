use std::alloc::{alloc as rust_alloc, dealloc as rust_dealloc, Layout};
use glam::{DMat3, DVec2, DVec3};
use crate::sable::MassData;
use crate::suspension::suspension_for_normal_mass;
use crate::wheels::{calculate_load, Wheel, calculate_relative_spring_tension};

const ABI_VERSION: u32 = 1;
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
    spring_scale: f64,
    wheel_count: u32,
    dt: f64,
    out: *mut f64,
) -> u32 {
    let Some(suspension) = suspension_for_normal_mass(
        total_mass, normal_mass, load_fraction, multiplier, spring_scale, wheel_count as usize, dt,
    ) else {
        return 0;
    };

    let out = unsafe { std::slice::from_raw_parts_mut(out, 3) };
    out.copy_from_slice(&[suspension.springiness, suspension.heave_damping, suspension.rotation_damping]);
    1
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn calc_distribution(mass: *const f64, wheels: *const f64, wheel_count: u32, dt: f64, fractions_out: *mut f64) -> f64 {
    let count = wheel_count as usize;
    if count == 0 || mass.is_null() || wheels.is_null() || fractions_out.is_null() {
        return f64::NAN;
    }

    let mass = unsafe { std::slice::from_raw_parts(mass, 14) };
    let wheels = unsafe { std::slice::from_raw_parts(wheels, count * 4) };
    let fractions_out = unsafe { std::slice::from_raw_parts_mut(fractions_out, count) };


    let mass_data = read_mass(mass);
    let wheels = read_wheels(&wheels);

    let center_of_mass = DVec2::new(mass_data.center_of_mass.x, mass_data.center_of_mass.z);
    let fractions = calculate_load(&wheels, center_of_mass);
    let spring_scale = calculate_relative_spring_tension(&wheels, &fractions, &mass_data, dt);

    fractions_out.copy_from_slice(&fractions);
    spring_scale
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