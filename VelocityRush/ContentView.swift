//
//  ContentView.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//

import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            GameView()
                .tabItem {
                    Image(systemName: "gamecontroller")
                    Text("Game")
                }
                .tag(0)
            
            StoreView()
                .tabItem {
                    Image(systemName: "cart")
                    Text("Store")
                }
                .tag(1)
            
            SettingsView()
                .tabItem {
                    Image(systemName: "gearshape")
                    Text("Settings")
                }
                .tag(2)
            
            ProfileView()
                .tabItem {
                    Image(systemName: "person")
                    Text("Profile")
                }
                .tag(3)
        }
        .accentColor(.blue)
    }
}

#Preview {
    ContentView()
}
