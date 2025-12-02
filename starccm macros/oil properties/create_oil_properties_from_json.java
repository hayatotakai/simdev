// Simcenter STAR-CCM+ macro: create_oil_properties_from_json.java
package macro;

import star.base.neo.*;
import star.common.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import star.material.*;

/**
 * Macro to create STAR-CCM+ materials for hydraulic oils from a JSON file.
 * <p>
 * Reads kinematic viscosities and density from a URL and generates:
 * - Scalar parameters for density and viscosity at two temperature points (P1, P2)
 * - Material database entries with dynamic viscosity and density definitions
 * - Groups in the global parameter manager for organization
 */
public class create_oil_properties_from_json extends StarMacro {

    private static final String JSON_URL = "https://hayatotakai.github.io/simdev/fluid_data.json";

    @Override
    public void execute() {
        executeMacro();
    }

    /** Main macro execution */
    private void executeMacro() {
        Simulation sim = getActiveSimulation();

        // Parse oils from JSON
        Map<String, OilProps> oilMap = parseJsonFromURL(JSON_URL);
        if (oilMap.isEmpty()) {
            sim.println("ERROR: Could not read any oil data from " + JSON_URL);
            return;
        }

        // Get all oil names
        Set<String> oils = oilMap.keySet();

        // Create top-level group for oil parameters
        ClientServerObjectGroup topGroup = createTopLevelGroup(sim, "user_oil_properties (DO NOT CHANGE)");

        // Ensure PARAM_Temp exists
        ScalarGlobalParameter tempParam = ensureParamTemp(sim);
        if (tempParam == null) return; // macro terminated if missing

        // Get integer string version for dynamic naming
        double tempValue = tempParam.getQuantity().getRawValue();
        String tempString = String.valueOf((int) Math.round(tempValue));

        // Create material database
        MaterialDataBase materialDB = sim.get(MaterialDataBaseManager.class).newMaterialDataBase();
        materialDB.setSaveOnSimulationSave(true);
        materialDB.setFilePath("D:\\Starccm_Oil_database\\iso_only");
        materialDB.setMdbName("user_MaterialBase_@" + tempString + "K");
        materialDB.setPresentationName("user_defined_liquids");
        DataBaseMaterialManager liquidsFolder = materialDB.addFolder();
        liquidsFolder.setPresentationName("Liquids");

        // Loop through oils and create parameters + materials
        for (String oilType : oils) {
            OilProps p = oilMap.get(oilType);
            if (p == null) continue;

            sim.println(oilType + " → ρ = " + p.density + " kg/m³ | ν₁ = " + p.kinVisc40 + " mm²/s | ν₂ = " + p.kinVisc100 + " mm²/s");

            // Create oil parameter group
            ClientServerObjectGroup oilGroup = createGroupUnder(topGroup, oilType);

            // Density
            createParameter(sim, oilGroup,
                    "Density@288.15K",
                    oilType + "_Density@288.15K",
                    p.density, "kg/m^3",
                    Dimensions.Builder().mass(1).volume(-1).build());

            // P1 parameters
            ClientServerObjectGroup P1Group = createGroupUnder(oilGroup, "P1");
            createParameter(sim, P1Group,
                    "Temperature@P1",
                    oilType + "_Temperature@P1",
                    p.temperature40, "K",
                    Dimensions.Builder().temperature(1).build());
            createParameter(sim, P1Group,
                    "kinematicViscosity@" + p.temperature40 + "K",
                    oilType + "_kinematicViscosity@P1",
                    p.kinVisc40 * 1e-6, "m^2/s",
                    Dimensions.Builder().length(2).time(-1).build());

            // P2 parameters
            ClientServerObjectGroup P2Group = createGroupUnder(oilGroup, "P2");
            createParameter(sim, P2Group,
                    "Temperature@P2",
                    oilType + "_Temperature@P2",
                    p.temperature100, "K",
                    Dimensions.Builder().temperature(1).build());
            createParameter(sim, P2Group,
                    "kinematicViscosity@" + p.temperature100 + "K",
                    oilType + "_kinematicViscosity@P2",
                    p.kinVisc100 * 1e-6, "m^2/s",
                    Dimensions.Builder().length(2).time(-1).build());

            // Create STAR-CCM+ liquid material
            DataBaseLiquid liquid = liquidsFolder.addLiquid();
            liquid.setTitle(oilType);
            liquid.setSymbol(oilType);
            liquid.addMaterialPropertyMethods(new StringVector(new String[]{
                    "Density.Constant", "DynamicViscosity.Constant", "SurfaceTension.Constant"}));

            // Density method
            DataBaseMaterialProperty densityProp = liquid.getMaterialProperty("Density");
            ConstantMaterialPropertyMethod densityMethod = (ConstantMaterialPropertyMethod) densityProp.getMethod("ConstantMaterialPropertyMethod");
            densityMethod.getQuantity().setDefinition("${" + oilType + "_Density@288.15K}*(1-0.00065*(${PARAM_Temp}-288.15))");

            // Dynamic viscosity method
            DataBaseMaterialProperty dynViscProp = liquid.getMaterialProperty("DynamicViscosity");
            ConstantMaterialPropertyMethod dynViscMethod = (ConstantMaterialPropertyMethod) dynViscProp.getMethod("ConstantMaterialPropertyMethod");
            dynViscMethod.getQuantity().setDefinition(
                    "(pow(10, pow(10, (log10(log10((${" + oilType + "_kinematicViscosity@P2} * 1e6) + 0.8)) - log10(log10((${" + oilType + "_kinematicViscosity@P1} * 1e6) + 0.8))) / (log10(${" + oilType + "_Temperature@P2}) - log10(${" + oilType + "_Temperature@P1})) * log10(${PARAM_Temp}) + log10(log10((${" + oilType + "_kinematicViscosity@P1} * 1e6) + 0.8)) - ((log10(log10((${" + oilType + "_kinematicViscosity@P2} * 1e6) + 0.8)) - log10(log10((${" + oilType + "_kinematicViscosity@P1} * 1e6) + 0.8))) / (log10(${" + oilType + "_Temperature@P2}) - log10(${" + oilType + "_Temperature@P1})) * log10(${" + oilType + "_Temperature@P1})))) - 0.8)*1e-6*(${" + oilType + "_Density@288.15K}*(1-0.00065*(${PARAM_Temp}-288.15)))");

            // Surface tension
            DataBaseMaterialProperty stProp = liquid.getMaterialProperty("SurfaceTension");
            ConstantMaterialPropertyMethod stMethod = (ConstantMaterialPropertyMethod) stProp.getMethod("ConstantMaterialPropertyMethod");
            stMethod.getQuantity().setValueAndUnits(0.03, sim.getUnitsManager().getObject("N/m"));
        }

        sim.println("All oils successfully created from JSON URL!");
    }

    /**
     * Creates a new scalar parameter and groups it under the specified parent.
     */
    private void createParameter(Simulation sim,
                                 ClientServerObjectGroup parentGroup,
                                 String paramName,
                                 String globalParamName,
                                 double value,
                                 String unitName,
                                 Dimensions dims) {
        ScalarGlobalParameter param = (ScalarGlobalParameter) sim.get(GlobalParameterManager.class)
                .createGlobalParameter(ScalarGlobalParameter.class, "Scalar");
        parentGroup.getGroupsManager().groupObjects(parentGroup.getPresentationName(), Collections.singletonList(param));
        param.setPresentationName(globalParamName);
        param.setDimensions(dims);
        param.getQuantity().setValueAndUnits(value, sim.getUnitsManager().getObject(unitName));
    }

    /** Ensures PARAM_Temp exists. Creates it with default if missing and terminates macro. */
    private ScalarGlobalParameter ensureParamTemp(Simulation sim) {
        GlobalParameterManager gpm = sim.get(GlobalParameterManager.class);
        ScalarGlobalParameter tempParam = null;

        try {
            tempParam = (ScalarGlobalParameter) gpm.getObject("PARAM_Temp");
        } catch (Exception e) {
            tempParam = null;
        }

        if (tempParam == null) {
            sim.println("ERROR: PARAM_Temp does not exist. Creating it now…");
            tempParam = (ScalarGlobalParameter) gpm.createGlobalParameter(ScalarGlobalParameter.class, "PARAM_Temp");
            tempParam.setPresentationName("PARAM_Temp");
            tempParam.setDimensions(Dimensions.Builder().temperature(1).build());
            tempParam.getQuantity().setValueAndUnits(288.15, sim.getUnitsManager().getObject("K"));
            sim.println("Created PARAM_Temp = 288.15 K");
            sim.println("Macro will now terminate. Re-run after setting PARAM_Temp.");
            return null;
        }

        return tempParam;
    }

    /** Creates a top-level group with the given name */
    private ClientServerObjectGroup createTopLevelGroup(Simulation sim, String name) {
        sim.get(GlobalParameterManager.class).getGroupsManager().createGroup("New Group");
        ClientServerObjectGroup group = (ClientServerObjectGroup) sim.get(GlobalParameterManager.class)
                .getGroupsManager().getObject("New Group");
        group.setPresentationName(name);
        return group;
    }

    /** Creates a subgroup under the given parent */
    private ClientServerObjectGroup createGroupUnder(ClientServerObjectGroup parent, String name) {
        parent.getGroupsManager().createGroup("New Group");
        ClientServerObjectGroup group = (ClientServerObjectGroup) parent.getGroupsManager().getObject("New Group");
        group.setPresentationName(name);
        return group;
    }

    /** Simple container for oil properties parsed from JSON */
    private static class OilProps {
        double density = 0;
        double kinVisc40 = 0;
        double kinVisc100 = 0;
        double temperature40 = 313.15;  // default
        double temperature100 = 373.15; // default
    }

    /**
     * Parses the JSON from a URL and extracts oil properties.
     *
     * @param urlString the URL of the JSON file
     * @return a map of oil name → OilProps
     */
    private Map<String, OilProps> parseJsonFromURL(String urlString) {
        Map<String, OilProps> map = new HashMap<>();
        try {
            URL url = URI.create(urlString).toURL();
            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String content = sb.toString();
            String[] blocks = content.split("\\},\\s*\"");

            for (String block : blocks) {
                Matcher nameMatcher = Pattern.compile("\"?([^\"]+)\"?\\s*:\\s*\\{").matcher(block);
                if (!nameMatcher.find()) continue;
                String fluidName = nameMatcher.group(1).trim();

                OilProps p = new OilProps();

                Matcher dMatcher = Pattern.compile("\"DensityAt15C\"\\s*:\\s*([0-9.]+)").matcher(block);
                if (dMatcher.find()) p.density = Double.parseDouble(dMatcher.group(1));

                Matcher kvMatcher = Pattern.compile("\\{\\s*\"temperature\"\\s*:\\s*([0-9.]+)\\s*,\\s*\"kinematicViscosity\"\\s*:\\s*([0-9.]+)\\s*\\}").matcher(block);
                int count = 0;
                while (kvMatcher.find()) {
                    double temp = Double.parseDouble(kvMatcher.group(1));
                    double kv = Double.parseDouble(kvMatcher.group(2));
                    if (count == 0) {
                        p.temperature40 = temp;
                        p.kinVisc40 = kv;
                    } else if (count == 1) {
                        p.temperature100 = temp;
                        p.kinVisc100 = kv;
                    }
                    count++;
                }

                if (p.density > 0) map.put(fluidName, p);
            }
        } catch (Exception e) {
            getActiveSimulation().println("Failed to read JSON from URL: " + e.getMessage());
        }
        return map;
    }
}
