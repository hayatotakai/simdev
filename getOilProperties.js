// Load fluid data from JSON
let fluidData = {}; // will be loaded from JSON

async function loadFluidData() {
    if (Object.keys(fluidData).length > 0) return; // already loaded
  if (typeof window !== "undefined" && typeof fetch !== "undefined") {
    // Browser mode
    const response = await fetch("fluid_data.json");
    fluidData = await response.json();
  } else if (typeof require !== "undefined") {
    // Node.js / ExecJS mode
    const fs = require("fs");
    const raw = fs.readFileSync("fluid_data.json", "utf8");
    fluidData = JSON.parse(raw);
  }
}

// fluidData = fetch('fluid_data.json')
/**
 * Get the density of a fluid at a given temperature.
 * 
 * @param {string} name - The name of the fluid.
 * @param {number} tK - Temperature in Kelvin.
 * @returns {number} Density in kg/m³, or NaN if fluid not found.
 */
function getDensityAtTemp(name, tK) {
  if (Object.keys(fluidData).length === 0) loadFluidData();
    const fluid = fluidData[name];
    if (!fluid) return NaN;
    return fluid.DensityAt15C * (1 - 0.00065 * (tK - 288.15));
}

/**
 * Get the kinematic viscosity of a fluid at a given temperature using Walther transform.
 * 
 * @param {string} name - The name of the fluid.
 * @param {number} tK - Temperature in Kelvin.
 * @returns {number} Kinematic viscosity in mm²/s, or NaN if data unavailable.
 */
function getKinViscAtTemp(name, tK) {
    if (Object.keys(fluidData).length === 0) loadFluidData();
    const kv = fluidData[name]?.["Kinematic Viscosity Limits"];
    if (!kv || kv.length < 2) return NaN;

    const T1 = kv[0].temperature;
    const V1 = kv[0].kinematicViscosity;
    const T2 = kv[1].temperature;
    const V2 = kv[1].kinematicViscosity;

    // Walther transform
    const W1 = Math.log10(Math.log10(V1 + 0.8));
    const W2 = Math.log10(Math.log10(V2 + 0.8));

    const m = (W2 - W1) / (Math.log10(T2) - Math.log10(T1));
    const n = W1 - m * Math.log10(T1);
console.log(`Walther params for ${name}: 
  W1 = ${W1.toFixed(6)}, 
  W2 = ${W2.toFixed(6)}, 
  m = ${m.toFixed(6)}, 
  n = ${n.toFixed(6)}`);
    const h = m * Math.log10(tK) + n;
    const viscAtT = Math.pow(10, Math.pow(10, h)) - 0.8;

    // Self-consistency check at T1 and T2
    const checkVisc1 = Math.pow(10, Math.pow(10, m * Math.log10(T1) + n)) - 0.8;
    const checkVisc2 = Math.pow(10, Math.pow(10, m * Math.log10(T2) + n)) - 0.8;

    const err1 = Math.abs((checkVisc1 - V1) / V1) * 100;
    const err2 = Math.abs((checkVisc2 - V2) / V2) * 100;

    if (err1 > 5) {
        console.warn(`Walther transform mismatch at T1 (${T1} K): expected ${V1}, got ${checkVisc1.toFixed(3)} (${err1.toFixed(2)}% error)`);
    }
    else{
    console.log(`Walther transform check passed at T1 (${T1} K): expected ${V1}, got ${checkVisc1.toFixed(3)} (${err1.toFixed(2)}% error)`);
    }
    if (err2 > 5) {
        console.warn(`Walther transform mismatch at T2 (${T2} K): expected ${V2}, got ${checkVisc2.toFixed(3)} (${err2.toFixed(2)}% error)`);
    }
    else{
    console.log(`Walther transform check passed at T2 (${T2} K): expected ${V2}, got ${checkVisc2.toFixed(3)} (${err2.toFixed(2)}% error)`);
    }
    return viscAtT;
}

/**
 * Get the dynamic viscosity of a fluid at a given temperature.
 * 
 * @param {string} name - The name of the fluid.
 * @param {number} tK - Temperature in Kelvin.
 * @returns {string} Dynamic viscosity in Pa·s as decimal string, or "NaN" if data unavailable.
 */
function getDynViscAtTemp(name, tK) {
    if (Object.keys(fluidData).length === 0) loadFluidData();
    const kinVisc = getKinViscAtTemp(name, tK); // mm²/s
    const density = getDensityAtTemp(name, tK); // kg/m³

// console.log(dynVisc);
  const dynVisc = (!isNaN(kinVisc) && !isNaN(density))
      ? kinVisc * 1e-6 * density // m²/s
      : NaN;
  return dynVisc; // return number, not string
}
/**
 * Get a list of all fluid names in the dataset.
 * 
 * @returns {string[]} Array of fluid names.
 */
function getFluidNames() {
    if (Object.keys(fluidData).length === 0) loadFluidData();
    return Object.keys(fluidData).sort();
}

// Export functions for both Node.js and browser
const OilProps = {
  getDensityAtTemp,
  getKinViscAtTemp,
  getDynViscAtTemp,
  getFluidNames
};

if (typeof module !== "undefined" && module.exports) {
  // Node.js / execjs
  module.exports = OilProps;
} else {
  // Browser
  window.OilProps = OilProps;
}