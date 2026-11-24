pub fn is_usable_mass(mass: f64) -> bool {
    mass.is_finite() && mass > 0.0
}