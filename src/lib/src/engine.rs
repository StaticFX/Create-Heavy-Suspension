use serde::Deserialize;
use crate::suspension::Suspension;

#[derive(Copy, Clone, Deserialize)]
pub struct Coordinate {
    x: f64,
    y: f64,
    z: f64
}

pub fn calc_suspension_for_wheel() -> Suspension {


    todo!()
}