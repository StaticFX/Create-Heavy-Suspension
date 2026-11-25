use std::alloc::{alloc as rust_alloc, dealloc as rust_dealloc, Layout};
use crate::suspension::suspension_for_normal_mass;

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


