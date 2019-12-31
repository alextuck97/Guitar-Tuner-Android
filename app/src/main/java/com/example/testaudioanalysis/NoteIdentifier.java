package com.example.testaudioanalysis;

import android.util.Pair;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

public class NoteIdentifier {

    final private double MIN_FREQUENCY = 10;
    private List<Pair<String, Double>> notes = new ArrayList<>();

    NoteIdentifier(){
        notes.add(new Pair<>("C", 32.7));
        notes.add(new Pair<>("Db", 34.65));
        notes.add(new Pair<>("D", 36.71));
        notes.add(new Pair<>("Eb", 38.89));
        notes.add(new Pair<>("E", 41.20));
        notes.add(new Pair<>("F", 43.65));
        notes.add(new Pair<>("Gb", 46.25));
        notes.add(new Pair<>("G", 49.00));
        notes.add(new Pair<>("Ab", 51.91));
         notes.add(new Pair<>("A",55.00));
        notes.add(new Pair<>("Bb", 58.27));
        notes.add(new Pair<>("B",61.74));
        notes.add(new Pair<>("C", 65.41));
    }

    public Pair<String, Double> identify(double frequency){

        if(frequency < MIN_FREQUENCY){
            return null; // Should work because Pair is not a primitive
        }

        double nFrequency = normalizeFrequency(frequency);

        Pair<String, Double> lowerBound = new Pair<>("",0.0);
        Pair<String, Double> upperBound = new Pair<>("",0.0);
        // Search through notes for what the frequency is between
        for(int i = 0 ; i < notes.size() - 1; i++){
            if (nFrequency >= notes.get(i).second && nFrequency <= notes.get(i + 1).second){
                lowerBound = notes.get(i);
                upperBound = notes.get(i + 1);
                break;
            }
        }

        if(lowerBound.first == "") return null;

        // Linearize notes to interpolate which is closest
        double upperLog = Math.log(upperBound.second) / Math.log(2);
        double lowerLog = Math.log(lowerBound.second) / Math.log(2);
        double frequencyLog = Math.log(nFrequency) / Math.log(2);

        double where = (frequencyLog - lowerLog) / (upperLog - lowerLog);
        Pair<String, Double> closestNote;

        String guess;
        Double off;

        if(where < 0.5 && where > 0.1){
            closestNote = lowerBound;
            //off = "Sharp";
        }
        else if (where >= 0.5 && where < 0.9){
            closestNote = upperBound;
            //off = "Flat";
        }
        else if(where > 0.9){
            closestNote = upperBound;
            //off = "Spot on";
        }
        else{
            closestNote = lowerBound;
            //off = "Spot on";
        }
        off = where;
        return new Pair<>(closestNote.first, off);
    }

    /***
    * Inflate or deflate frequency to within bounds of this.notes
     ***/
    private double normalizeFrequency(double frequency){

        double low = notes.get(0).second;
        double high = notes.get(notes.size() - 1).second;

        if(frequency < low){
            while(frequency < low){
                frequency *= 2;
            }
        }
        else if (frequency > high){
            while(frequency > high){
                frequency /= 2;
            }
        }

        return frequency;
    }

}
