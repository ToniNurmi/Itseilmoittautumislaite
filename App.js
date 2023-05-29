import React, {useState} from 'react';
import './App.css';

function App() {

  const [patients, setPatients] = useState([]);

  const barcodeFilter = (barcode) => {
   fetch(`http://192.168.1.237:5140/api/Patients/${barcode}`)
      .then(response => response.json())
      .then((patients) => {
        setPatients(patients);

        //Formatoi aika
        const dateString = patients[0].time;
        const date = new Date(dateString);
        const options = {
          day: 'numeric',
          month: 'long',
          year: 'numeric',
          hour: 'numeric',
          minute: 'numeric',
          timeZone: 'UTC',
        };
        const formatter = new Intl.DateTimeFormat('fi-FI', options);
        const formattedDate = formatter.format(date);

        window.Android.connectPrinter(
          patients[0].id, 
          formattedDate, 
          patients[0].description, 
          patients[0].room, 
          "Kävele portaat ylös ja käänny 4 kertaa vasemmalle. ÅÖÄ ÜôÕÇΩД")
      })
      .catch((err) => {
        console.log(err.message);
      });
  }

  return (
    <div className="App">
      <h2>Lue henkilökortti</h2>
        <button onClick={() => barcodeFilter("string")}>Lue viivakoodi</button>
        {/* Tässä pitäisi lukea viivakoodi, ottaa string (?) ylös ja filtteröidä tulokset sen mukaan ja lähettää ne Androidille*/}
    </div> 
  );
}

export default App;

