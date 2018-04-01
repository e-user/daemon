const hello = {
  op: 'hello',
  version: '0.1.0'
}

function handleMessage ({ data }) {
  console.log(`Received ${data}`)
  const { op } = JSON.parse(data)
  console.log(op)
}

export default {
  create ({ url }) {
    const { host, pathname, protocol } = new URL(url)
    const ws = protocol === 'https' ? 'wss' : 'ws'
    const socket = new WebSocket(`${ws}://${host}${pathname}socket`)

    socket.addEventListener('open', () => {
      socket.send(JSON.stringify(hello))
    })

    socket.addEventListener('message', handleMessage)
  }
}
