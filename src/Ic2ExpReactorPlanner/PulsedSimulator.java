package Ic2ExpReactorPlanner;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/**
 *
 * @author Brian McCloud
 */
public class PulsedSimulator extends SwingWorker<Void, String> {

    private final Reactor reactor;
    
    private final JTextArea output;
    
    private final JPanel[][] reactorButtonPanels;
    
    private final boolean[][] alreadyBroken = new boolean[6][9];
    
    private final int initialHeat;
    
    private double minEUoutput = Double.MAX_VALUE;
    
    private double maxEUoutput = 0.0;
    
    private double minHeatOutput = Double.MAX_VALUE;
    
    private double maxHeatOutput = 0.0;
    
    private final int onPulseDuration;
    
    private final int offPulseDuration;
    
    private final int suspendTemp;
    
    private final int resumeTemp;
    
    private boolean active = true;
    
    private int nextOffTime = 0;
    
    private int nextOnTime = 0;
    
    private int redstoneUsed = 0;
    
    private int lapisUsed = 0;
    
    public PulsedSimulator(final Reactor reactor, final JTextArea output, final JPanel[][] reactorButtonPanels, final int initialHeat, 
            final int onPulseDuration, final int offPulseDuration, final int suspendTemp, final int resumeTemp) {
        this.reactor = reactor;
        this.output = output;
        this.reactorButtonPanels = reactorButtonPanels;
        this.initialHeat = initialHeat;
        this.onPulseDuration = onPulseDuration;
        this.offPulseDuration = offPulseDuration;
        this.suspendTemp = suspendTemp;
        this.resumeTemp = resumeTemp;
        this.nextOffTime = onPulseDuration;
    }
    
    @Override
    protected Void doInBackground() throws Exception {
        long startTime = System.nanoTime();
        int reactorTicks = 0;
        int cooldownTicks = 0;
        int totalRodCount = 0;
        try {
            publish(""); //NOI18N
            publish(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("SIMULATION_STARTED"));
            reactor.setCurrentHeat(initialHeat);
            reactor.clearVentedHeat();
            double minReactorHeat = initialHeat;
            double maxReactorHeat = initialHeat;
            boolean reachedBurn = initialHeat >= 0.4 * reactor.getMaxHeat();
            boolean reachedEvaporate = initialHeat >= 0.5 * reactor.getMaxHeat();
            boolean reachedHurt = initialHeat >= 0.7 * reactor.getMaxHeat();
            boolean reachedLava = initialHeat >= 0.85 * reactor.getMaxHeat();
            boolean reachedExplode = false;
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) {
                    ReactorComponent component = reactor.getComponentAt(row, col);
                    if (component != null) {
                        component.clearCurrentHeat();
                        component.clearDamage();
                        totalRodCount += component.getRodCount();
                    }
                    publish(String.format("R%dC%d:0xC0C0C0", row, col)); //NOI18N
                }
            }
            if (totalRodCount == 0) {
                publish(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("NO_FUEL_RODS_FOUND"));
                return null;
            }
            double lastEUoutput = 0.0;
            double totalEUoutput = 0.0;
            double lastHeatOutput = 0.0;
            double totalHeatOutput = 0.0;
            double maxGeneratedHeat = 0.0;
            boolean allFuelRodsDepleted = false;
            do {
                reactor.clearEUOutput();
                reactor.clearVentedHeat();
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorComponent component = reactor.getComponentAt(row, col);
                        if (component != null) {
                            component.preReactorTick();
                        }
                    }
                }
                if (active) {
                    allFuelRodsDepleted = true;
                }
                double generatedHeat = 0.0;
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorComponent component = reactor.getComponentAt(row, col);
                        if (component != null && !component.isBroken()) {
                            if (allFuelRodsDepleted && component.getRodCount() > 0) {
                                allFuelRodsDepleted = false;
                            }
                            if (active) {
                                generatedHeat += component.generateHeat();
                            }
                            maxReactorHeat = Math.max(reactor.getCurrentHeat(), maxReactorHeat);
                            minReactorHeat = Math.min(reactor.getCurrentHeat(), minReactorHeat);
                            component.dissipate();
                            maxReactorHeat = Math.max(reactor.getCurrentHeat(), maxReactorHeat);
                            minReactorHeat = Math.min(reactor.getCurrentHeat(), minReactorHeat);
                            component.transfer();
                            maxReactorHeat = Math.max(reactor.getCurrentHeat(), maxReactorHeat);
                            minReactorHeat = Math.min(reactor.getCurrentHeat(), minReactorHeat);
                        }
                        if (maxReactorHeat >= 0.4 * reactor.getMaxHeat() && !reachedBurn) {
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("REACTOR_BURN_TIME"), reactorTicks));
                            reachedBurn = true;
                        }
                        if (maxReactorHeat >= 0.5 * reactor.getMaxHeat() && !reachedEvaporate) {
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("REACTOR_EVAPORATE_TIME"), reactorTicks));
                            reachedEvaporate = true;
                        }
                        if (maxReactorHeat >= 0.7 * reactor.getMaxHeat() && !reachedHurt) {
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("REACTOR_HURT_TIME"), reactorTicks));
                            reachedHurt = true;
                        }
                        if (maxReactorHeat >= 0.85 * reactor.getMaxHeat() && !reachedLava) {
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("REACTOR_LAVA_TIME"), reactorTicks));
                            reachedLava = true;
                        }
                        if (maxReactorHeat >= reactor.getMaxHeat() && !reachedExplode) {
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("REACTOR_EXPLODE_TIME"), reactorTicks));
                            reachedExplode = true;
                        }
                    }
                }
                maxGeneratedHeat = Math.max(generatedHeat, maxGeneratedHeat);
                if (active) {
                    for (int row = 0; row < 6; row++) {
                        for (int col = 0; col < 9; col++) {
                            ReactorComponent component = reactor.getComponentAt(row, col);
                            if (component != null && !component.isBroken()) {
                                component.generateEnergy();
                            }
                        }
                    }
                }
                lastEUoutput = reactor.getCurrentEUoutput();
                totalEUoutput += lastEUoutput;
                lastHeatOutput = reactor.getVentedHeat();
                totalHeatOutput += lastHeatOutput;
                if (reactor.getCurrentHeat() <= reactor.getMaxHeat()) {
                    reactorTicks++;
                    if (active) {
                        if (reactor.getCurrentHeat() >= suspendTemp || reactorTicks >= nextOffTime) {
                            active = false;
                            nextOnTime = reactorTicks + offPulseDuration;
                        }
                    } else {
                        if (reactor.getCurrentHeat() <= resumeTemp && reactorTicks >= nextOnTime) {
                            active = true;
                            nextOffTime = reactorTicks + onPulseDuration;
                        }
                    }
                    minEUoutput = Math.min(lastEUoutput, minEUoutput);
                    maxEUoutput = Math.max(lastEUoutput, maxEUoutput);
                    minHeatOutput = Math.min(lastHeatOutput, minHeatOutput);
                    maxHeatOutput = Math.max(lastHeatOutput, maxHeatOutput);
                }
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorComponent component = reactor.getComponentAt(row, col);
                        if (component != null && component.isBroken() && !alreadyBroken[row][col] && !component.getClass().getName().contains("FuelRod")) { //NOI18N
                            publish(String.format("R%dC%d:0xFF0000", row, col)); //NOI18N
                            alreadyBroken[row][col] = true;
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("BROKE_TIME"), row, col, reactorTicks));
                        }
                        if (reactor.isUsingReactorCoolantInjectors()) {
                            if (component instanceof RshCondensator && component.getCurrentHeat() > 17000 && !component.isBroken()) {
                                ((RshCondensator) component).injectCoolant();
                                redstoneUsed++;
                            } else if (component instanceof LzhCondensator && component.getCurrentHeat() > 85000 && !component.isBroken()) {
                                ((LzhCondensator) component).injectCoolant();
                                lapisUsed++;
                            }
                        }
                    }
                }
            } while (reactor.getCurrentHeat() <= reactor.getMaxHeat() && (!allFuelRodsDepleted || lastEUoutput > 0 || lastHeatOutput > 0) && reactorTicks < 5000000);
            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("MIN_TEMP"), minReactorHeat));
            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("MAX_TEMP"), maxReactorHeat));
            if (reactor.getCurrentHeat() <= reactor.getMaxHeat()) {
                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("CYCLE_COMPLETE_TIME"), reactorTicks));
                if (reactorTicks > 0) {
                    if (reactor.isFluid()) {
                        publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("HEAT_OUTPUTS"), 2 * totalHeatOutput, 2 * totalHeatOutput / reactorTicks, 2 * minHeatOutput, 2 * maxHeatOutput));
                        publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("EFFICIENCY"), totalHeatOutput / reactorTicks / 4 / totalRodCount, minHeatOutput / 4 / totalRodCount, maxHeatOutput / 4 / totalRodCount));
                    } else {
                        publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("EU_OUTPUTS"), totalEUoutput, minEUoutput / 20.0, maxEUoutput / 20.0, totalEUoutput / (reactorTicks * 20)));
                        publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("EFFICIENCY"), totalEUoutput / reactorTicks / 100 / totalRodCount, minEUoutput / 100 / totalRodCount, maxEUoutput / 100 / totalRodCount));
                    }
                }

                if (reactor.getCurrentHeat() > 0.0) {
                    publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("REACTOR_REMAINING_HEAT"), reactor.getCurrentHeat()));
                }
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 9; col++) {
                        ReactorComponent component = reactor.getComponentAt(row, col);
                        if (component != null && !component.isBroken()) {
                            if (component.getCurrentHeat() > 0.0) {
                                publish(String.format("R%dC%d:0xFFA500", row, col)); //NOI18N
                                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("COMPONENT_REMAINING_HEAT"), row, col, component.getCurrentHeat()));
                            }
                        }
                    }
                }
            } else {
                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("REACTOR_OVERHEATED_TIME"), reactorTicks));
            }
            double totalEffectiveVentCooling = 0.0;
            double totalVentCoolingCapacity = 0.0;
            double totalCellCooling = 0.0;
            double totalCondensatorCooling = 0.0;
            
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) {
                    ReactorComponent component = reactor.getComponentAt(row, col);
                    if (component != null) {
                        if (component.getVentCoolingCapacity() > 0) {
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("USED_COOLING"), row, col, component.getEffectiveVentCooling(), component.getVentCoolingCapacity()));
                            totalEffectiveVentCooling += component.getEffectiveVentCooling();
                            totalVentCoolingCapacity += component.getVentCoolingCapacity();
                        } else if (component.getBestCellCooling() > 0) {
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("RECEIVED_HEAT"), row, col, component.getBestCellCooling()));
                            totalCellCooling += component.getBestCellCooling();
                        } else if (component.getBestCondensatorCooling() > 0) {
                            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("RECEIVED_HEAT"), row, col, component.getBestCondensatorCooling()));
                            totalCondensatorCooling += component.getBestCondensatorCooling();
                        }
                    }
                }
            }
                    
            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("TOTAL_VENT_COOLING"), totalEffectiveVentCooling, totalVentCoolingCapacity));
            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("TOTAL_CELL_COOLING"), totalCellCooling));
            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("TOTAL_CONDENSATOR_COOLING"), totalCondensatorCooling));
            publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("MAX_HEAT_GENERATED"), maxGeneratedHeat));
            if (redstoneUsed > 0) {
                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("REDSTONE_USED"), redstoneUsed));
            }
            if (lapisUsed > 0) {
                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("LAPIS_USED"), lapisUsed));
            }
            double totalCooling = totalEffectiveVentCooling + totalCellCooling + totalCondensatorCooling;
            if (totalCooling >= maxGeneratedHeat) {
                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("EXCESS_COOLING"), totalCooling - maxGeneratedHeat));
            } else {
                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("EXCESS_HEATING"), maxGeneratedHeat - totalCooling));
            }
            //return null;
        } catch (Throwable e) {
            if (cooldownTicks == 0) {
                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("ERROR_AT_REACTOR_TICK"), reactorTicks));
            } else {
                publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("ERROR_AT_COOLDOWN_TICK"), cooldownTicks));
            }
            publish(e.toString(), " ", Arrays.toString(e.getStackTrace())); //NOI18N
        }
        long endTime = System.nanoTime();
        publish(String.format(java.util.ResourceBundle.getBundle("Ic2ExpReactorPlanner/Bundle").getString("SIMULATION_TIME"), (endTime - startTime) / 1e9));
        return null;
    }

    @Override
    protected void process(List<String> chunks) {
        if (!isCancelled()) {
            for (String chunk : chunks) {
                if (chunk.isEmpty()) {
                    output.setText(""); //NOI18N
                } else {
                    if (chunk.matches("R\\dC\\d:.*")) { //NOI18N
                        String temp = chunk.substring(5);
                        int row = chunk.charAt(1) - '0';
                        int col = chunk.charAt(3) - '0';
                        if (temp.startsWith("0x")) { //NOI18N
                            reactorButtonPanels[row][col].setBackground(Color.decode(temp));
                        } else if (temp.startsWith("+")) { //NOI18N
                            final ReactorComponent component = reactor.getComponentAt(row, col);
                            if (component != null) {
                                component.info += "\n" + temp.substring(1); //NOI18N
                            }
                        } else {
                            final ReactorComponent component = reactor.getComponentAt(row, col);
                            if (component != null) {
                                component.info = temp;
                            }
                        }
                    } else {
                        output.append(chunk);
                    }
                }
            }
        }
    }
    
}
