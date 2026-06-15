/**
 * Sweep — אפליקציית אנשי קשר
 * שלב 1: שלד ריק להוכחת צינור הבנייה.
 */
import React from 'react';
import {SafeAreaView, StatusBar, StyleSheet, Text, View} from 'react-native';

function App(): React.JSX.Element {
  return (
    <SafeAreaView style={styles.root}>
      <StatusBar barStyle="light-content" backgroundColor="#0B1220" />
      <View style={styles.center}>
        <Text style={styles.logo}>Sweep</Text>
        <Text style={styles.tag}>אפליקציית אנשי הקשר</Text>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>השלד עובד ✓</Text>
        </View>
        <Text style={styles.note}>שלב 1 — הצינור מותקן ובונה. המודולים בדרך.</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#0B1220'},
  center: {flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24},
  logo: {fontSize: 56, fontWeight: '800', color: '#FFFFFF', letterSpacing: 1},
  tag: {fontSize: 18, color: '#9FB3C8', marginTop: 8},
  badge: {
    marginTop: 28,
    backgroundColor: '#16A34A',
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 999,
  },
  badgeText: {color: '#FFFFFF', fontSize: 16, fontWeight: '700'},
  note: {marginTop: 22, color: '#64748B', fontSize: 14, textAlign: 'center'},
});

export default App;
