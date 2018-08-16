package com.sixrr.metrics.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.reference.SoftReference;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.Reference;
import java.text.DecimalFormat;
import java.util.*;

@State(name = "EnergyInsights", storages = @Storage(file = "energy.insights.xml"))
public final class EnergyInsightsConfig implements PersistentStateComponent<EnergyInsightsConfig> {
    private String FilePath;
    private String ProfileName;
    private Dictionary<String,String> Values=new Hashtable<>();
    public static final String BUNDLE = "com.sixrr.metrics.config.configuration";
    private static Reference<ResourceBundle> INSTANCE;
    private float energy;
    private int m;

    private EnergyInsightsConfig(){}
    public static EnergyInsightsConfig getInstance() {
        return ServiceManager.getService(EnergyInsightsConfig.class);
    }
    @Nullable
    @Override
    public EnergyInsightsConfig getState() {    return this;    }
    @Override
    public void loadState(EnergyInsightsConfig state) { XmlSerializerUtil.copyBean(state, this);    }

    public void setFilePath(String s){  FilePath=s; }
    private String getFilePath(){    return FilePath;    }

    public void setProfileName(String s){   ProfileName=s;  }
    private String getProfileName(){ return ProfileName; }

    private static ResourceBundle getBundle() {
        ResourceBundle bundle = SoftReference.dereference(INSTANCE);
        if (bundle == null) {
            bundle = ResourceBundle.getBundle(BUNDLE);
            INSTANCE = new SoftReference<ResourceBundle>(bundle);
        }
        return bundle;
    }

    public String getImpactFor(String s){
        return getCCFor(s)+getBundle().getString(s+"Impact");
    }

    public float getAlphaFor(String s){ return Float.valueOf(getBundle().getString(s+"Alpha"));}

    public void calculateOverallEnergy(String s,float x){
        if(x!=0)
            energy+=(getAlphaFor(s)*getCorrelationFor(s)*x);
        m++;
    }

    public String getOverallEnergy(){
        if(m<12)
            return "Calculating";
        if(energy>0)
            return "Increased";
        if(energy==0)
            return "-";
        return "Decreased";
    }

    public int getCorrelationFor(String s){
        String s1=getBundle().getString(s+"Correlation");
        if(s1.compareToIgnoreCase("-ve")==0)
            return -1;
        return +1;
    }
    public char getCCFor(String s){
        String s1=getBundle().getString(s+"Correlation");
        if(s1.compareToIgnoreCase("-ve")==0)
            return '-';
        return '+';
    }

    public Object getValueOf(String s1){
        if(Values.get(s1)!=null)
            return Values.get(s1);
        return "";
    }

    public void generateValues(){
        m=0;
        energy=0;
        try {
            if(!(new File(getFilePath()+"\\Prev_"+getProfileName()+".csv").exists())){
                Scanner nRead=new Scanner(new File(getFilePath()+"\\New_"+getProfileName()+".csv"));
                String tmp=nRead.nextLine();
                while(true) {
                    tmp = nRead.nextLine();
                    if(tmp.compareToIgnoreCase("Finished")==0)
                        break;
                    String[] metrics = tmp.split(",");
                    tmp = nRead.nextLine();
                    while (!(tmp.compareToIgnoreCase("cfinish") == 0))
                        tmp=nRead.nextLine();
                    for (int i = 1; i < metrics.length; i++)
                        Values.put(metrics[i], "-:" +getImpactFor(metrics[i])+":0%");
                }
                nRead.close();
                m=12;
                return;
            }
            Scanner pRead=new Scanner(new File(getFilePath()+"\\Prev_"+getProfileName()+".csv"));
            Scanner nRead=new Scanner(new File(getFilePath()+"\\New_"+getProfileName()+".csv"));
            String tmp=nRead.nextLine();
            String tmp1=pRead.nextLine();
            while(true) {
                tmp = nRead.nextLine();
                tmp1 = pRead.nextLine();
                if(tmp.compareToIgnoreCase("Finished")==0)
                    break;
                String[] metrics = tmp.split(",");
                float[] averages1 = new float[metrics.length];
                float[] averages2 = new float[metrics.length];
                for (int i = 0; i < averages1.length; i++) {
                    averages1[i] = 0;
                    averages2[i] = 0;
                }
                float n1=0,n2=0;
                tmp = nRead.nextLine();
                tmp1 = pRead.nextLine();
                while (tmp1.compareToIgnoreCase("cfinish") != 0) {
                    String[] v2 = tmp1.split(",");
                    for (int i = 1; i < v2.length; i++) {
                        float x;
                        if (v2[i].contains("%")) {
                            x = Float.parseFloat(v2[i].substring(0, v2[i].length() - 1));
                        } else {
                            x = Float.parseFloat(v2[i]);
                        }
                        averages2[i] += x;
                    }
                    n2++;
                    tmp1 = pRead.nextLine();
                }
                while(tmp.compareToIgnoreCase("cfinish")!=0) {
                    String[] v1 = tmp.split(",");
                    for (int i = 1; i < v1.length; i++) {
                        float x;
                        if (v1[i].contains("%")) {
                            x = Float.parseFloat(v1[i].substring(0, v1[i].length() - 1));
                        } else {
                            x = Float.parseFloat(v1[i]);
                        }
                        averages1[i] += x;
                    }
                    n1++;
                    tmp=nRead.nextLine();
                }
                if (n1 > 0) {
                    for (int i = 1; i < metrics.length; i++) {
                        float x=(averages1[i]/n1);
                        float y=(averages2[i]/n2);
                        float ch=x-y;
                        float ra=ch/y;
                        DecimalFormat df = new DecimalFormat();
                        df.setMaximumFractionDigits(2);
                        if(y==0)
                            ra=x/100;
                        calculateOverallEnergy(metrics[i],ra);
                        float rap=ra*100;
                        if (ch*getCorrelationFor(metrics[i]) > 0)
                            Values.put(metrics[i], "inc:" + getImpactFor(metrics[i])+":"+df.format(rap)+"%");
                        else if (ch == 0)
                            Values.put(metrics[i], "-:" + getImpactFor(metrics[i])+":"+df.format(rap)+"%");
                        else
                            Values.put(metrics[i], "dec:" + getImpactFor(metrics[i])+":"+df.format(rap)+"%");
                    }
                }
            }
            nRead.close();
            pRead.close();
        } catch (FileNotFoundException ignored) {
        }
    }

}
