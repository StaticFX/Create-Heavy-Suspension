
// stuff coming from sable

use glam::{DMat3, DVec3};

pub struct MassData {
    pub total_mass: f64,
    pub inverse_mass: f64,
    pub center_of_mass: DVec3,
    pub inverse_inertia: DMat3,
}

impl MassData {

    // also stolen from sables code, thx rhyan
    pub fn inverse_normal_mass(&self, point: DVec3, normal: DVec3) -> f64 {
        let lever = point - self.center_of_mass;
        let rotation_axis = lever.cross(normal.normalize());
        self.inverse_mass + rotation_axis.dot(self.inverse_inertia * rotation_axis)
    }
}