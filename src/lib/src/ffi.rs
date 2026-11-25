use std::alloc::{alloc as rust_alloc, dealloc as rust_dealloc, Layout};

const ABI_VERSION: u32 = 1;
const ALIGN: usize = 8;

#[unsafe(no_mangle)]
pub extern "C" fn abi_version() -> u32 {
    ABI_VERSION
}

#[unsafe(no_mangle)]
pub extern "C" fn alloc(size: usize) -> *mut u8 {
    if size == 0 {
        return std::ptr::null_mut();
    }

    unsafe {
        rust_alloc(Layout::from_size_align(size, ALIGN).unwrap())
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn dealloc(ptr: *mut u8, size: usize) {
    if size == 0 || ptr.is_null() {
        return;
    }

    unsafe {
        rust_dealloc(ptr, Layout::from_size_align(size, ALIGN).unwrap());
    }
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn sum_i32(ptr: *const i32, len: usize) -> i64 {
    let data = std::slice::from_raw_parts(ptr, len);
    data.iter().map(|&x| x as i64).sum()
}