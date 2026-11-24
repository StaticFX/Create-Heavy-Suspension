use serde::Serialize;
use tsify::Tsify;

#[derive(Clone, Copy, Debug, Tsify, Serialize)]
pub struct Suspension {
    springiness: f64,
    heave_damping: f64,
    rotation_damping: f64
}

impl Suspension {
    pub fn new(springiness: f64, heave_damping: f64, rotation_damping: f64) -> Self {
        Suspension {
            springiness,
            heave_damping,
            rotation_damping
        }
    }
}