//
//package com.quorum.tessera.config.cli;
//
//import Config;
//
//
//public class Main {
//    
//    public static void main(String... args) throws Exception {
//        
//        CliDelegate cliDelegate = CliDelegate.instance();
//        try {
//            Config config = cliDelegate.execute(args);
//            System.out.println(config.getJdbcConfig());
//            
//        } catch(Exception ex) {
//            System.err.println(ex.getMessage());
//        }
//        
//        
//    }
//}
